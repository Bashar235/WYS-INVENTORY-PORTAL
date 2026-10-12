package com.wys.inventory.viewmodel;

import com.wys.inventory.event.StockChangeBridge;
import com.wys.inventory.model.AuditLog;
import com.wys.inventory.model.Product;
import com.wys.inventory.model.User;
import com.wys.inventory.repository.ActionRepository;
import com.wys.inventory.repository.AuditLogRepository;
import com.wys.inventory.repository.RoleRepository;
import com.wys.inventory.repository.UserRepository;
import com.wys.inventory.service.AccessControlService;
import com.wys.inventory.service.PermissionService;
import com.wys.inventory.service.PermissionService.PermissionMatrix;
import com.wys.inventory.service.PermissionService.RolePermissions;
import com.wys.inventory.service.ProductService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Path;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueue;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zul.Include;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backs dashboard.zul.
 * Provides live inventory telemetry and an executive control center for SUPER_ADMIN.
 */
@VariableResolver(DelegatingVariableResolver.class)
public class DashboardViewModel {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter AUDIT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String UNCATEGORIZED = "Uncategorized";

    @WireVariable
    private ProductService productService;

    @WireVariable
    private AccessControlService accessControlService;

    @WireVariable
    private UserRepository userRepository;

    @WireVariable
    private RoleRepository roleRepository;

    @WireVariable
    private ActionRepository actionRepository;

    @WireVariable
    private AuditLogRepository auditLogRepository;

    @WireVariable
    private PermissionService permissionService;

    private String username;
    private String role;
    private boolean superAdmin;

    // Navigation state
    private String currentSubView = "~./zul/catalog.zul";

    // Chart & Inventory metrics
    private int totalProducts;
    private long totalUnits;
    private BigDecimal totalInventoryValue;
    private String formattedTotalValue = "$0.00";
    private int inStockCount;
    private int lowStockCount;
    private int outOfStockCount;
    private List<CategoryBar> categoryBars = new ArrayList<>();
    private String lastUpdated = "";

    // Super Admin specific telemetry
    private long totalUsersCount;
    private long totalRolesCount;
    private long totalActionsCount;
    private long totalAuditLogsCount;
    private List<AuditLog> recentAuditLogs = new ArrayList<>();
    private List<User> systemUsers = new ArrayList<>();
    private List<RoleCoverage> roleCoverage = new ArrayList<>();

    // Live update plumbing
    private Desktop desktop;
    private EventQueue<Event> queue;
    private EventListener<Event> changeListener;

    @Init
    public void init() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            this.username = auth.getName();
            this.role = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .findFirst()
                    .orElse("USER");
        }

        // Determine if current user is SUPER_ADMIN
        String currentRoleCode = (accessControlService != null) ? accessControlService.getRoleCode() : "";
        this.superAdmin = "SUPER_ADMIN".equalsIgnoreCase(currentRoleCode)
                || (role != null && role.equalsIgnoreCase("SUPER_ADMIN"));

        loadStats();
        subscribeToChanges();
    }

    @Destroy
    public void destroy() {
        try {
            if (queue != null && changeListener != null) {
                queue.unsubscribe(changeListener);
            }
        } catch (Exception ignored) {
        }
        changeListener = null;
        queue = null;
    }

    // ------------------------------------------------------------------
    // Commands & Actions
    // ------------------------------------------------------------------

    @Command
    @NotifyChange("*")
    public void refresh() {
        loadStats();
    }

    @Command
    public void navigateToModule(@BindingParam("target") String target) {
        if (target == null || target.trim().isEmpty()) return;
        this.currentSubView = target;

        Include mainContent = null;
        Desktop currentDesktop = Executions.getCurrent().getDesktop();
        if (currentDesktop != null) {
            for (org.zkoss.zk.ui.Page p : currentDesktop.getPages()) {
                Component c = p.getFellowIfAny("mainContent");
                if (c instanceof Include) {
                    mainContent = (Include) c;
                    break;
                }
            }
        }
        if (mainContent == null) {
            try {
                mainContent = (Include) Path.getComponent("/mainContent");
            } catch (Exception ignored) {}
        }
        if (mainContent != null) {
            mainContent.setSrc(target);
        }
    }

    // ------------------------------------------------------------------
    // Live Stock Event Queue
    // ------------------------------------------------------------------

    private void subscribeToChanges() {
        desktop = Executions.getCurrent().getDesktop();
        queue = EventQueues.lookup(StockChangeBridge.QUEUE, EventQueues.APPLICATION, true);
        changeListener = event -> {
            if (desktop == null || !desktop.isAlive()) {
                return;
            }
            loadStats();
            BindUtils.postNotifyChange(null, null, this, "*");
        };
        queue.subscribe(changeListener);
    }

    // ------------------------------------------------------------------
    // Data Loading
    // ------------------------------------------------------------------

    private void loadStats() {
        List<Product> products = (productService != null)
                ? productService.findAll()
                : new ArrayList<>();

        int ok = 0, low = 0, out = 0;
        long units = 0;
        BigDecimal totalVal = BigDecimal.ZERO;
        Map<String, CategoryBar> groups = new LinkedHashMap<>();

        for (Product p : products) {
            int qty = p.getQuantity();
            boolean isOut = qty <= 0;
            boolean isLow = !isOut && qty < p.getReorderLevel();

            if (isOut) out++; else if (isLow) low++; else ok++;
            units += Math.max(qty, 0);

            BigDecimal price = (p.getUnitPrice() != null) ? p.getUnitPrice() : BigDecimal.ZERO;
            totalVal = totalVal.add(BigDecimal.valueOf(Math.max(qty, 0)).multiply(price));

            String name = (p.getCategory() == null || p.getCategory().trim().isEmpty())
                    ? UNCATEGORIZED : p.getCategory().trim();
            CategoryBar bar = groups.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new CategoryBar(name));
            bar.productCount++;
            bar.units += Math.max(qty, 0);
            if (isLow || isOut) bar.lowCount++;
        }

        List<CategoryBar> bars = new ArrayList<>(groups.values());
        bars.sort(Comparator.comparingLong((CategoryBar b) -> b.units).reversed()
                .thenComparing(b -> b.name, String.CASE_INSENSITIVE_ORDER));

        long max = bars.isEmpty() ? 0 : bars.get(0).units;
        for (CategoryBar b : bars) {
            double pct = (max > 0) ? (b.units * 100.0 / max) : 0;
            if (b.units > 0 && pct < 3) pct = 3;
            b.widthStyle = String.format(Locale.ROOT, "width:%.1f%%", pct);
        }

        this.totalProducts = products.size();
        this.totalUnits = units;
        this.totalInventoryValue = totalVal;
        this.formattedTotalValue = NumberFormat.getCurrencyInstance(Locale.US).format(totalVal);
        this.inStockCount = ok;
        this.lowStockCount = low;
        this.outOfStockCount = out;
        this.categoryBars = bars;
        this.lastUpdated = LocalTime.now().format(CLOCK);

        // Load Super Admin specific data if user is superadmin
        if (this.superAdmin) {
            try {
                if (userRepository != null) {
                    this.totalUsersCount = userRepository.count();
                    this.systemUsers = userRepository.findAll();
                }
                if (roleRepository != null) {
                    this.totalRolesCount = roleRepository.count();
                }
                if (actionRepository != null) {
                    this.totalActionsCount = actionRepository.count();
                }
                if (auditLogRepository != null) {
                    this.totalAuditLogsCount = auditLogRepository.count();
                    this.recentAuditLogs = auditLogRepository.findTop10ByOrderByTimestampDesc();
                }
                loadRoleCoverage();
            } catch (Exception ex) {
                // Ignore transient db lookup errors during startup
            }
        }
    }

    /**
     * Builds the "RBAC coverage" widget: how many of the declarable permission keys each
     * role currently holds. Reads through {@link PermissionService} so the lazy
     * collections are initialised inside a transaction and never leak into the view.
     */
    private void loadRoleCoverage() {
        if (permissionService == null) return;
        PermissionMatrix matrix = permissionService.loadMatrix();
        int total = matrix.getMenus().size() + matrix.getActions().size();
        List<RoleCoverage> rows = new ArrayList<>();
        for (RolePermissions r : matrix.getRoles()) {
            rows.add(new RoleCoverage(r, total));
        }
        this.roleCoverage = rows;
    }

    // ------------------------------------------------------------------
    // Formatters & UI Helpers
    // ------------------------------------------------------------------

    public String formatTimestamp(Instant instant) {
        if (instant == null) return "—";
        try {
            return AUDIT_DATE_FORMAT.format(instant);
        } catch (Exception e) {
            return instant.toString();
        }
    }

    public String getChangeBadgeClass(int amount) {
        if (amount > 0) return "badge badge-success";
        if (amount < 0) return "badge badge-danger";
        return "badge badge-neutral";
    }

    public String formatChangeAmount(int amount) {
        if (amount > 0) return "+" + amount;
        return String.valueOf(amount);
    }

    public String getRoleBadgeClass(String roleCode) {
        if ("SUPER_ADMIN".equalsIgnoreCase(roleCode)) return "role-pill role-pill-super";
        if ("ADMIN".equalsIgnoreCase(roleCode)) return "role-pill role-pill-admin";
        if ("MANAGER".equalsIgnoreCase(roleCode)) return "role-pill role-pill-manager";
        return "role-pill";
    }

    public String pct(int count) {
        if (totalProducts == 0) return "0%";
        return Math.round(count * 100.0 / totalProducts) + "%";
    }

    // ------------------------------------------------------------------
    // Role & Permission Checks
    // ------------------------------------------------------------------

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public boolean isAdmin() {
        if (isSuperAdmin()) return true;
        if (accessControlService != null) {
            return "ADMIN".equalsIgnoreCase(accessControlService.getRoleCode());
        }
        return false;
    }

    public boolean isManager() {
        if (isSuperAdmin()) return true;
        if (accessControlService != null) {
            return "MANAGER".equalsIgnoreCase(accessControlService.getRoleCode())
                    || accessControlService.canDo("ITEM_CREATE_SUBMIT");
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Getters for ZUL
    // ------------------------------------------------------------------

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public int getTotalProducts() { return totalProducts; }
    public long getTotalUnits() { return totalUnits; }
    public String getTotalUnitsFormatted() {
        return NumberFormat.getIntegerInstance(Locale.US).format(totalUnits);
    }
    public BigDecimal getTotalInventoryValue() { return totalInventoryValue; }
    public String getFormattedTotalValue() { return formattedTotalValue; }
    public int getInStockCount() { return inStockCount; }
    public int getLowStockCount() { return lowStockCount; }
    public int getOutOfStockCount() { return outOfStockCount; }
    public List<CategoryBar> getCategoryBars() { return categoryBars; }

    public String getSummaryText() {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        int n = categoryBars.size();
        return nf.format(totalUnits) + " units across " + n + (n == 1 ? " category" : " categories");
    }

    public String getLiveText() {
        return "Live · updated " + lastUpdated;
    }

    public String getInStockPct()    { return pct(inStockCount); }
    public String getLowStockPct()   { return pct(lowStockCount); }
    public String getOutOfStockPct() { return pct(outOfStockCount); }

    public String getDonutStyle() {
        if (totalProducts == 0) {
            return "--p-ok:0%;--p-low:0%";
        }
        double okEnd = inStockCount * 100.0 / totalProducts;
        double lowEnd = (inStockCount + lowStockCount) * 100.0 / totalProducts;
        return String.format(Locale.ROOT, "--p-ok:%.2f%%;--p-low:%.2f%%", okEnd, lowEnd);
    }

    public String getCurrentSubView() { return currentSubView; }
    public void setCurrentSubView(String currentSubView) { this.currentSubView = currentSubView; }

    public long getTotalUsersCount() { return totalUsersCount; }
    public long getTotalRolesCount() { return totalRolesCount; }
    public long getTotalActionsCount() { return totalActionsCount; }
    public long getTotalAuditLogsCount() { return totalAuditLogsCount; }
    public List<AuditLog> getRecentAuditLogs() { return recentAuditLogs; }
    public List<User> getSystemUsers() { return systemUsers; }

    public String getRolesCountText() {
        return totalRolesCount + " Roles";
    }

    public String getActionsCountText() {
        return totalActionsCount + " Granular Action Rights";
    }

    public String getProductsCountText() {
        return totalProducts + " Active Catalog SKUs";
    }

    public String getLowStockCountText() {
        return lowStockCount + " Low Stock Alerts";
    }

    public String getQuantityShift(AuditLog log) {
        if (log == null) return "—";
        return log.getPreviousQuantity() + " → " + log.getNewQuantity();
    }

    // ------------------------------------------------------------------
    // Header, greeting & module gating
    // ------------------------------------------------------------------

    /** Time-of-day aware greeting used in the dashboard header and super admin banner. */
    public String getGreetingText() {
        String who = (username == null || username.trim().isEmpty()) ? "there" : username.trim();
        int hour = LocalTime.now().getHour();
        String part;
        if (hour < 12)      part = "Good morning";
        else if (hour < 18) part = "Good afternoon";
        else                part = "Good evening";
        return part + ", " + who;
    }

    /** "Signed in as superadmin · SUPER_ADMIN" identity line for the header. */
    public String getIdentityLine() {
        String who = (username == null || username.trim().isEmpty()) ? "unknown user" : username;
        String roleName = (role == null || role.trim().isEmpty())
                ? "USER" : role.replace('_', ' ');
        return "Signed in as " + who + " · " + roleName;
    }

    /** Secondary line explaining what the dashboard is showing. */
    public String getDashboardSubtitle() {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        return nf.format(totalProducts) + " catalogued SKUs · "
                + nf.format(totalUnits) + " units on hand · "
                + lowStockCount + " low-stock alert" + (lowStockCount == 1 ? "" : "s");
    }

    /** "Live · updated 14:22:05" — drives the pulsing status pill in the header. */
    public String getHeaderLiveText() {
        return lastUpdated.isEmpty() ? "Live" : "Live · updated " + lastUpdated;
    }

    /** Compact alert summary used as the inventory card subtitle. */
    public String getAlertSummaryText() {
        if (totalProducts == 0) return "No products yet";
        if (outOfStockCount == 0 && lowStockCount == 0) return "Every SKU is above its reorder level";
        return outOfStockCount + " out of stock · " + lowStockCount + " below reorder level";
    }

    /**
     * Module cards are driven by menu permissions rather than hard-coded role names, so
     * a role whose menus were edited on the permission page sees the cards immediately.
     * The {@code is} prefix is required: ZK's expression language resolves properties
     * through the JavaBeans Introspector and cannot reach a plain {@code canXxx()} method.
     */
    public boolean isCanOpenPermissionAdmin() { return canAccessMenu("MENU_PERM_ADMIN"); }
    public boolean isCanOpenCatalog()         { return canAccessMenu("MENU_CATALOG"); }
    public boolean isCanOpenItemCreator()     { return canAccessMenu("MENU_ITEM_CREATOR"); }
    public boolean isCanOpenStockAdjust()     { return canAccessMenu("MENU_STOCK_ADJUST"); }

    private boolean canAccessMenu(String menuCode) {
        return accessControlService != null && accessControlService.canAccess(menuCode);
    }

    // ---- Super Admin RBAC coverage ----

    public List<RoleCoverage> getRoleCoverage() { return roleCoverage; }

    public boolean isRoleCoverageEmpty() { return roleCoverage == null || roleCoverage.isEmpty(); }

    public String getAuditLogsCountText() {
        return NumberFormat.getIntegerInstance(Locale.US).format(totalAuditLogsCount)
                + (totalAuditLogsCount == 1 ? " retained event" : " retained events");
    }

    /**
     * One RBAC coverage bar: a role, how many of the declarable permission keys it holds,
     * and the matching bar width.
     */
    public static class RoleCoverage {
        private final String  roleCode;
        private final String  roleName;
        private final int     granted;
        private final int     total;
        private final String  widthStyle;
        private final String  toneClass;

        RoleCoverage(RolePermissions role, int total) {
            this.roleCode = role.getRoleCode();
            this.roleName = role.getRoleName();
            this.total    = total;
            this.granted  = role.getGrantedTotal();
            double pct = (total > 0) ? (granted * 100.0 / total) : 0;
            this.widthStyle = String.format(Locale.ROOT, "width:%.1f%%", Math.min(pct, 100.0));
            this.toneClass  = pct >= 100 ? "cov-fill cov-fill-full"
                            : pct >= 50 ? "cov-fill cov-fill-high"
                                        : "cov-fill cov-fill-low";
        }

        public String getRoleCode()    { return roleCode; }
        public String getRoleName()    { return roleName; }
        public int    getGranted()     { return granted; }
        public int    getTotal()       { return total; }
        public String getWidthStyle()  { return widthStyle; }
        public String getToneClass()   { return toneClass; }

        public String getCountText() {
            return granted + " / " + total + " rights";
        }

        public String getPctText() {
            if (total == 0) return "0%";
            return Math.round(granted * 100.0 / total) + "%";
        }
    }

    // ------------------------------------------------------------------
    // Category Bar Model
    // ------------------------------------------------------------------

    public static class CategoryBar {
        private final String name;
        private int productCount;
        private long units;
        private int lowCount;
        private String widthStyle = "width:0%";

        CategoryBar(String name) { this.name = name; }

        public String getName() { return name; }
        public int getLowCount() { return lowCount; }
        public String getWidthStyle() { return widthStyle; }

        public String getLowText() { return lowCount + " low"; }

        public String getDetailText() {
            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            return productCount + (productCount == 1 ? " product" : " products")
                    + " · " + nf.format(units) + (units == 1 ? " unit" : " units");
        }
    }
}