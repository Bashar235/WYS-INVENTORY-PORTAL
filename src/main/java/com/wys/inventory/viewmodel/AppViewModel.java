package com.wys.inventory.viewmodel;

import com.wys.inventory.model.Menu;
import com.wys.inventory.service.AccessControlService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zul.Include;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Shell-level ViewModel for app.zul.
 * Sidebar menus are built dynamically from the DB-driven AccessControlService.
 */
@VariableResolver(DelegatingVariableResolver.class)
public class AppViewModel {

    @WireVariable
    private AccessControlService accessControlService;

    private String username;
    private String currentView = "~./zul/dashboard.zul";

    @Init
    public void init() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            this.username = auth.getName();
        }
    }

@Command
    @NotifyChange("currentView")
    public void navigateToProfile() {
        this.currentView = "~./zul/profile.zul";
        Include inc = (Include) org.zkoss.zk.ui.Path.getComponent("/mainContent");
        if (inc != null) {
            inc.setSrc("~./zul/profile.zul");
        }
    }

    @Command
    public void logout() {
        HttpServletRequest request   = (HttpServletRequest) Executions.getCurrent().getNativeRequest();
        HttpServletResponse response = (HttpServletResponse) Executions.getCurrent().getNativeResponse();
        new SecurityContextLogoutHandler().logout(request, response,
            SecurityContextHolder.getContext().getAuthentication());
        Executions.sendRedirect("/perform_logout");
    }

    @Command
    @NotifyChange("currentView")
    public void navigate(@BindingParam("page") String page,
                         @ContextParam(ContextType.COMPONENT) Component comp) {
        this.currentView = page;
        if (comp != null && comp.getPage() != null) {
            Include inc = (Include) comp.getPage().getFellowIfAny("mainContent");
            if (inc != null) {
                inc.setSrc(page);
            }
        }
    }

    // ---- Dynamic RBAC Helpers ----

    /** Returns ordered menus the current user is allowed to see. */
    public List<Menu> getAllowedMenus() {
        return accessControlService.getAllowedMenus();
    }

    /** Human-readable role code for display in the header badge. */
    public String getRole() {
        return accessControlService.getRoleCode();
    }

    /**
     * Full CSS class for the header role tag. ZK does not evaluate
     * "@load(...)" embedded inside literal sclass text, so the whole
     * class string is produced here and bound with a single @load.
     */
    public String getRoleTagClass() {
        String role = getRole();
        if ("SUPER_ADMIN".equals(role) || "ADMIN".equals(role)) {
            return "shell-role-tag shell-role-admin";
        }
        return "shell-role-tag shell-role-manager";
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(accessControlService.getRoleCode());
    }

    // ---- Standard Getters ----

    public String getUsername() { return username; }

    public String getAvatarInitial() {
        if (username != null && !username.trim().isEmpty()) {
            return username.trim().substring(0, 1).toUpperCase();
        }
        return "U";
    }

    public String getCurrentView() { return currentView; }
    public void   setCurrentView(String v) { this.currentView = v; }
}
