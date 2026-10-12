package com.wys.inventory.viewmodel;

import com.wys.inventory.model.User;
import com.wys.inventory.service.AccessControlService;
import com.wys.inventory.service.PermissionService;
import com.wys.inventory.service.PermissionService.PermissionItem;
import com.wys.inventory.service.PermissionService.PermissionMatrix;
import com.wys.inventory.service.PermissionService.RolePermissions;
import com.wys.inventory.service.PermissionService.UserPermissions;
import com.wys.inventory.repository.UserRepository;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ViewModel for permissionAdmin.zul — the Super Admin permission management GUI.
 *
 * Every one of the declarable permission keys is rendered as an interactive row with a
 * real switch control, a label and a description:
 *
 *   MENU_DASHBOARD, MENU_CATALOG, MENU_ITEM_CREATOR, MENU_STOCK_ADJUST, MENU_PERM_ADMIN
 *   PERM_DASHBOARD_ACCESS, PERM_CATALOG_READ, PERM_CATALOG_CREATE, ...
 *
 * Toggling a switch writes the join-table row immediately; the bulk
 * enable/disable helpers rewrite a whole group in a single transaction.
 */
@VariableResolver(DelegatingVariableResolver.class)
public class PermissionAdminViewModel {

    /** Permissions that must stay granted to SUPER_ADMIN to avoid locking the portal out. */
    private static final Set<String> PROTECTED_FOR_SUPER_ADMIN =
            Set.of("MENU_PERM_ADMIN", "PERM_ADMIN_ACCESS");

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    @WireVariable
    private PermissionService permissionService;

    @WireVariable
    private AccessControlService accessControlService;

    @WireVariable
    private UserRepository userRepository;

    /** A single searchable entry shown in the top combobox — either a role or a user. */
    public static class SelectionItem {
        public enum Kind { ROLE, USER }

        private final Kind     kind;
        private final Long     id;
        private final String   code;    // role code or username
        private final String   label;   // role name or username
        private final String   sub;     // extra line (role name for users, grant count for roles)
        private final String   kindLabel;

        SelectionItem(Kind kind, Long id, String code, String label, String sub) {
            this.kind      = kind;
            this.id        = id;
            this.code      = code;
            this.label     = label;
            this.sub       = sub != null ? sub : "";
            this.kindLabel = kind == Kind.ROLE ? "Role" : "User";
        }

        static SelectionItem role(RolePermissions r, long userCount) {
            return new SelectionItem(Kind.ROLE, r.getId(), r.getRoleCode(), r.getRoleName(),
                    userCount + " user" + (userCount == 1 ? "" : "s"));
        }

        static SelectionItem user(User u) {
            String role = u.getRole() != null ? u.getRole().getRoleName() : "(no role)";
            return new SelectionItem(Kind.USER, u.getId(), u.getUsername(), u.getUsername(),
                    role);
        }

        public Kind    getKind()       { return kind; }
        public Long    getId()         { return id; }
        public String  getCode()       { return code; }
        public String  getLabel()      { return label; }
        public String  getSub()        { return sub; }
        public String  getKindLabel()  { return kindLabel; }
        public boolean isRole()        { return kind == Kind.ROLE; }
        public boolean isUser()        { return kind == Kind.USER; }

        @Override public String toString() { return label; }
    }

    // ---- GUI state ----
    private List<PermissionItem> menuItems    = new ArrayList<>();
    private List<PermissionItem> actionItems  = new ArrayList<>();
    private List<RolePermissions> roles       = new ArrayList<>();
    private RolePermissions selectedRole;

    /** Target for permission edits: ROLE or USER. */
    public enum EditingTarget { ROLE, USER }

    private EditingTarget editingTarget = EditingTarget.ROLE;

    /** When editingTarget == USER, this holds the selected user's ID. */
    private Long selectedUserId;

    /** Codes currently granted to {@link #selectedRole} or {@link #selectedUserId}, mirrored for cheap EL lookups. */
    private Set<String> grantedMenuCodes   = new LinkedHashSet<>();
    private Set<String> grantedActionCodes = new LinkedHashSet<>();

    /** All roles rendered as searchable entries in the top combobox. */
    private List<SelectionItem> selectionItems = new ArrayList<>();

    /** The entry currently highlighted/selected in the combobox — used only as a bridge
     *  so the ZK combobox knows which item to mark selected. The real state of
     *  truth is {@link #selectedRole}. */
    private SelectionItem selectedSelection;

    public SelectionItem getSelectedSelection() { return selectedSelection; }
    public void setSelectedSelection(SelectionItem selectedSelection) { this.selectedSelection = selectedSelection; }

    /** Users belonging to the currently selected role — shown for transparency. */
    private List<UserItem> roleUsers = new ArrayList<>();

    /** Filtered view of roleUsers, updated by the search bar. */
    private List<UserItem> filteredRoleUsers = new ArrayList<>();

    /** Actions grouped by their parent module, for the action rights card. */
    private List<ActionModuleGroup> actionModuleGroups = new ArrayList<>();

    private boolean  authorized = true;
    private String   statusMessage = "";
    private int      lastChangedCount = 0;

    /** Text currently typed into the role-search combobox. */
    private String searchQuery = "";

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    /** Which list the "Role or user" selector shows: ROLE or USER. */
    private EditingTarget selectionMode = EditingTarget.ROLE;

    public EditingTarget getSelectionMode() { return selectionMode; }
    public boolean isSelectionModeRole() { return selectionMode == EditingTarget.ROLE; }
    public boolean isSelectionModeUser()  { return selectionMode == EditingTarget.USER; }

    /** Text typed into the selector search box. */
    private String selectorSearchQuery = "";

    public String getSelectorSearchQuery() { return selectorSearchQuery; }
    public void setSelectorSearchQuery(String selectorSearchQuery) {
        this.selectorSearchQuery = selectorSearchQuery;
    }

    /** Tracks pending permission changes that haven't been saved yet.
     *  Key = item code, Value = target granted state (true = grant, false = revoke). */
    private Map<String, Boolean> pendingChanges = new LinkedHashMap<>();

    /** Whether a save operation is in progress. */
    private boolean saving = false;

    /** Save result message for UI feedback. */
    private String saveMessage = "";

    @Init
    public void init() {
        // Check if the current user has the SUPER_ADMIN role
        String currentRoleCode = accessControlService.getRoleCode();
        authorized = "SUPER_ADMIN".equals(currentRoleCode);
        if (!authorized) {
            statusMessage = "Access denied — This page is restricted to Super Admin users only.";
            Clients.showNotification("Access denied: Super Admin role required.",
                    Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 4000);
            return;
        }
        reload();
    }

    // ==================================================================
    // Commands
    // ==================================================================

    /** Re-reads the whole matrix from the database (source of truth). */
    @Command
    @NotifyChange("*")
    public void reload() {
        PermissionMatrix matrix = permissionService.loadMatrix();
        menuItems   = matrix.getMenus();
        actionItems = matrix.getActions();
        roles       = matrix.getRoles();
        refreshSelectionList();
        if (selectedRole != null) {
            RolePermissions refreshed = findRole(selectedRole.getRoleCode());
            if (refreshed != null) applySelection(refreshed);
        } else if (!roles.isEmpty()) {
            applySelection(roles.get(0));
        }
        reloadUsers();
        if (editingTarget == EditingTarget.USER && selectedUserId != null) {
            loadUserOverrides(selectedUserId);
        }
        actionModuleGroups = buildActionModuleGroups();
    }

    /** Switches the matrix to the chosen role. */
    @Command
    @NotifyChange("*")
    public void selectRole(@BindingParam("role") RolePermissions role) {
        if (!authorized || role == null) return;
        applySelection(role);
    }

    /** Switches the "Role or user" selector between the Roles and Users lists. */
    @Command
    @NotifyChange("*")
    public void setSelectionMode(@BindingParam("mode") String mode) {
        if (mode == null) return;
        try {
            this.selectionMode = EditingTarget.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        if (this.selectionMode == EditingTarget.ROLE) {
            backToRoleEditing();
        }
        refreshSelectionList();
    }

    /** Maps backend role codes to the display labels used in the dropdown. */
    private String getRoleDisplayName(String roleCode) {
        if (roleCode == null) return "";
        switch (roleCode) {
            case "SUPER_ADMIN": return "Superadmin";
            case "ADMIN":       return "Admin";
            case "MANAGER":     return "Manager";
            default:            return roleCode;
        }
    }

    /** Rebuilds the combobox entries from the current matrix. */
    @Command
    @NotifyChange("*")
    public void refreshSelectionList() {
        if (selectionMode == EditingTarget.USER) {
            selectionItems = userRepository.searchUsers("").stream()
                    .map(SelectionItem::user)
                    .collect(Collectors.toList());
        } else {
            selectionItems = roles.stream()
                    .map(r -> {
                        long userCount = permissionService.countUsersInRole(r.getRoleCode());
                        return SelectionItem.role(r, userCount);
                    })
                    .collect(Collectors.toList());
        }
    }

    /** Returns combobox entries matching the typed search text. Filters the
     *  list currently shown by the segmented control (roles or users). */
    @Command
    @NotifyChange("*")
    public List<SelectionItem> searchSelection(String term) {
        String q = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        return selectionItems.stream()
                .filter(s -> s.getLabel().toLowerCase(Locale.ROOT).contains(q)
                        || s.getSub().toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toList());
    }

    /** Live filter for the selector list box. */
    @Command
    @NotifyChange("filteredSelectionItems")
    public void filterSelector(@BindingParam("term") String term) {
        this.selectorSearchQuery = term == null ? "" : term;
    }

    /** The selector list after applying the typed search text. */
    public List<SelectionItem> getFilteredSelectionItems() {
        String q = selectorSearchQuery == null ? "" : selectorSearchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return selectionItems;
        return selectionItems.stream()
                .filter(s -> s.getLabel().toLowerCase(Locale.ROOT).contains(q)
                        || s.getSub().toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toList());
    }

    /** Handles a choice from the selector list — either a role or a user. */
    @Command
    @NotifyChange("*")
    public void selectItem(@BindingParam("item") SelectionItem item) {
        SelectionItem selected = item != null ? item : selectedSelection;
        if (selected == null) return;
        selectedSelection = selected;
        if (selected.isRole()) {
            editingTarget = EditingTarget.ROLE;
            selectedUserId = null;
            RolePermissions role = findRole(selected.getCode());
            if (role != null) {
                applySelection(role);
                statusMessage = "Editing role: " + role.getRoleName();
            }
        } else {
            // Select a specific user for per-user permission editing
            editingTarget = EditingTarget.USER;
            selectedUserId = selected.getId();
            RolePermissions role = findRoleByItemId(selected.getId());
            if (role != null) applySelection(role);
            loadUserOverrides(selectedUserId);
        }
        reloadUsers();
        pendingChanges.clear();
    }

    /** True when the given selector row is the currently selected entry. */
    public boolean isSelectorRowActive(SelectionItem item) {
        if (item == null) return false;
        if (item.isRole()) {
            return selectedRole != null
                    && item.getCode() != null
                    && item.getCode().equals(selectedRole.getRoleCode());
        }
        if (item.isUser()) {
            return selectedUserId != null
                    && item.getId() != null
                    && item.getId().equals(selectedUserId);
        }
        return false;
    }

    /** Explicitly selects a user by ID and switches to user-scoped permission editing. */
    @Command
    @NotifyChange("*")
    public void selectUser(@BindingParam("userId") Long userId) {
        if (userId == null) return;
        editingTarget = EditingTarget.USER;
        selectedUserId = userId;
        RolePermissions role = findRoleByItemId(userId);
        if (role != null) applySelection(role);
        loadUserOverrides(userId);
        reloadUsers();
        pendingChanges.clear();
    }

    /** Switches back to role-level permission editing. */
    @Command
    @NotifyChange("*")
    public void backToRoleEditing() {
        editingTarget = EditingTarget.ROLE;
        selectedUserId = null;
        grantedMenuCodes.clear();
        grantedActionCodes.clear();
        if (selectedRole != null) {
            refreshRoleCounts();
        }
        statusMessage = "Back to role-level editing: " + (selectedRole != null ? selectedRole.getRoleName() : "(none)");
        pendingChanges.clear();
    }

    /** Loads user-specific permission overrides into the granted sets. */
    private void loadUserOverrides(Long userId) {
        if (userId == null) {
            grantedMenuCodes.clear();
            grantedActionCodes.clear();
            return;
        }
        try {
            UserPermissions up = permissionService.loadUserPermissions(userId);
            grantedMenuCodes = up.getMenuCodes();
            grantedActionCodes = up.getActionCodes();
            statusMessage = "Editing effective permissions for user: " + up.getUsername()
                    + " (role: " + up.getRoleCode()
                    + ") — role inheritance plus per-user grants/denies";
        } catch (RuntimeException ex) {
            grantedMenuCodes.clear();
            grantedActionCodes.clear();
            statusMessage = "Could not load user overrides: " + ex.getMessage();
        }
    }

    /** Refreshes the list of users belonging to the currently selected role. */
    @Command
    @NotifyChange("*")
    public void reloadUsers() {
        roleUsers = new ArrayList<>();
        filteredRoleUsers = new ArrayList<>();
        if (selectedRole != null) {
            userRepository.searchUsers("")
                    .stream()
                    .filter(u -> u.getRole() != null && u.getRole().getId().equals(selectedRole.getId()))
                    .sorted(Comparator.comparing(User::getUsername))
                    .map(u -> new UserItem(u, selectedRole.getId()))
                    .forEach(roleUsers::add);
            filteredRoleUsers = new ArrayList<>(roleUsers);
        }
    }

    /** Filters the currently displayed user list by username or role ID. */
    @Command
    @NotifyChange("*")
    public void searchRoleUsers(String term) {
        String q = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredRoleUsers = new ArrayList<>(roleUsers);
        } else {
            filteredRoleUsers = roleUsers.stream()
                    .filter(u -> {
                        String username = u.getUser().getUsername().toLowerCase(Locale.ROOT);
                        String roleCode = u.getUser().getRole() != null
                                ? u.getUser().getRole().getRoleCode().toLowerCase(Locale.ROOT)
                                : "";
                        String idStr = String.valueOf(u.getUser().getId());
                        return username.contains(q) || roleCode.contains(q) || idStr.contains(q);
                    })
                    .collect(Collectors.toList());
        }
    }

    private RolePermissions findRoleByItemId(Long userId) {
        if (userId == null) return null;
        Long roleId = userRepository.findById(userId)
                .map(u -> u.getRole() != null ? u.getRole().getId() : null)
                .orElse(null);
        if (roleId == null) return null;
        return roles.stream()
                .filter(r -> roleId.equals(r.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Flips a single permission switch in memory (pending change).
     * Changes are NOT persisted until save() is called.
     * Dispatches to the menu or the action join table depending on the item kind.
     * Supports both role-based and user-specific permission editing.
     */
    @Command
    @NotifyChange("*")
    public void toggle(@BindingParam("item") PermissionItem item) {
        if (!guard(item)) return;

        boolean target = !isGranted(item);
        if (!target && isLocked(item)) {
            deny(item.getCode() + " is protected and cannot be revoked from " + SUPER_ADMIN
                    + " — it would lock every administrator out of the portal.");
            return;
        }

        // Track the pending change
        pendingChanges.put(item.getCode(), target);
        // Immediately update the in-memory granted sets for instant UI feedback
        set(item, target);
        statusMessage = "Pending: " + (target ? "Grant " : "Revoke ") + item.getCode()
                + " for " + getEditingTargetLabel();
    }

    /** Convenience command used by the per-row Grant button. */
    @Command
    @NotifyChange("*")
    public void grant(@BindingParam("item") PermissionItem item) {
        if (!guard(item)) return;
        pendingChanges.put(item.getCode(), true);
        set(item, true);
        statusMessage = "Pending: Grant " + item.getCode();
    }

    /** Convenience command used by the per-row Revoke button. */
    @Command
    @NotifyChange("*")
    public void revoke(@BindingParam("item") PermissionItem item) {
        if (!guard(item)) return;
        if (isLocked(item)) {
            deny(item.getCode() + " is protected and cannot be revoked from " + SUPER_ADMIN);
            return;
        }
        pendingChanges.put(item.getCode(), false);
        set(item, false);
        statusMessage = "Pending: Revoke " + item.getCode();
    }

    // ---- Save pending changes ----

    /** Persists all pending permission changes to the database. */
    @Command
    @NotifyChange("*")
    public void save() {
        if (!authorized) {
            deny("Access denied — the PERM_ADMIN_ACCESS permission is required.");
            return;
        }
        if (pendingChanges.isEmpty()) {
            statusMessage = "No pending changes to save.";
            Clients.showNotification("No pending changes", Clients.NOTIFICATION_TYPE_INFO,
                    null, "top_center", 2000);
            return;
        }

        saving = true;
        saveMessage = "Saving...";

        try {
            if (editingTarget == EditingTarget.USER && selectedUserId != null) {
                saveUserOverrides();
            } else {
                saveRolePermissions();
            }
            clearPendingChanges();
            saving = false;
            saveMessage = "Saved successfully";
            statusMessage = "Changes saved to database.";
            Clients.showNotification("All changes saved", Clients.NOTIFICATION_TYPE_INFO,
                    null, "top_center", 2500);
        } catch (RuntimeException ex) {
            saving = false;
            saveMessage = "Save failed: " + ex.getMessage();
            statusMessage = "Save failed: " + ex.getMessage();
            Clients.showNotification(saveMessage, Clients.NOTIFICATION_TYPE_ERROR,
                    null, "top_center", 5000);
            reload();
        }
    }

    private void saveRolePermissions() {
        if (selectedRole == null) {
            throw new IllegalStateException("No role selected");
        }

        // The in-memory granted sets already reflect every pending toggle,
        // so they ARE the full desired state. Persist them in a single
        // transactional replace — never split into grant/revoke lists,
        // which the service would misinterpret as menu vs action ids.
        List<Long> desiredMenuIds   = desiredMenuIds();
        List<Long> desiredActionIds = desiredActionIds();

        permissionService.replacePermissions(selectedRole.getRoleCode(),
                desiredMenuIds, desiredActionIds);

        accessControlService.invalidate();
        reload();
    }

    private void saveUserOverrides() {
        if (selectedUserId == null) {
            throw new IllegalStateException("No user selected");
        }

        // Same as the role path: the granted sets hold the user's full
        // desired effective state. The service reconciles them against the
        // role inheritance and writes grant/deny rows accordingly.
        List<Long> desiredMenuIds   = desiredMenuIds();
        List<Long> desiredActionIds = desiredActionIds();

        permissionService.replaceUserPermissions(selectedUserId,
                desiredMenuIds, desiredActionIds);

        // Invalidate the target user's cached session so the change takes
        // effect immediately instead of after a re-login.
        String targetUsername = userRepository.findById(selectedUserId)
                .map(User::getUsername)
                .orElse(null);
        AccessControlService.invalidateForUser(targetUsername);
        // And refresh the current (admin) session cache.
        accessControlService.invalidate();
        reload();
    }

    /** Ids of every menu whose code is currently in the granted set. */
    private List<Long> desiredMenuIds() {
        return menuItems.stream()
                .filter(i -> grantedMenuCodes.contains(i.getCode()))
                .map(PermissionItem::getId)
                .collect(Collectors.toList());
    }

    /** Ids of every action whose code is currently in the granted set. */
    private List<Long> desiredActionIds() {
        return actionItems.stream()
                .filter(i -> grantedActionCodes.contains(i.getCode()))
                .map(PermissionItem::getId)
                .collect(Collectors.toList());
    }

    /** Clears all pending changes and resets in-memory state to match database. */
    @Command
    @NotifyChange("*")
    public void clearPendingChanges() {
        pendingChanges.clear();
        if (editingTarget == EditingTarget.USER && selectedUserId != null) {
            loadUserOverrides(selectedUserId);
        } else if (selectedRole != null) {
            refreshRoleCounts();
        }
        statusMessage = "Pending changes cleared.";
        saveMessage = "";
    }

    private PermissionItem findItemByCode(String code) {
        return menuItems.stream()
                .filter(i -> i.getCode().equals(code))
                .findFirst()
                .orElseGet(() -> actionItems.stream()
                        .filter(i -> i.getCode().equals(code))
                        .findFirst()
                        .orElse(null));
    }

    // ---- Bulk operations (track as pending changes) ----

    @Command
    @NotifyChange("*")
    public void grantAllMenus() {
        if (!guardAll()) return;
        for (PermissionItem item : menuItems) {
            if (!isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), true);
                set(item, true);
            }
        }
        statusMessage = "Pending: Grant all menus";
    }

    @Command
    @NotifyChange("*")
    public void revokeAllMenus() {
        if (!guardAll()) return;
        for (PermissionItem item : menuItems) {
            if (isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), false);
                set(item, false);
            }
        }
        statusMessage = "Pending: Revoke all menus";
    }

    @Command
    @NotifyChange("*")
    public void grantAllActions() {
        if (!guardAll()) return;
        for (PermissionItem item : actionItems) {
            if (!isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), true);
                set(item, true);
            }
        }
        statusMessage = "Pending: Grant all actions";
    }

    @Command
    @NotifyChange("*")
    public void revokeAllActions() {
        if (!guardAll()) return;
        for (PermissionItem item : actionItems) {
            if (isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), false);
                set(item, false);
            }
        }
        statusMessage = "Pending: Revoke all actions";
    }

    @Command
    @NotifyChange("*")
    public void grantEverything() {
        if (!guardAll()) return;
        for (PermissionItem item : menuItems) {
            if (!isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), true);
                set(item, true);
            }
        }
        for (PermissionItem item : actionItems) {
            if (!isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), true);
                set(item, true);
            }
        }
        statusMessage = "Pending: Grant all permissions";
    }

    @Command
    @NotifyChange("*")
    public void revokeEverything() {
        if (!guardAll()) return;
        for (PermissionItem item : menuItems) {
            if (isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), false);
                set(item, false);
            }
        }
        for (PermissionItem item : actionItems) {
            if (isGranted(item) && !isLocked(item)) {
                pendingChanges.put(item.getCode(), false);
                set(item, false);
            }
        }
        statusMessage = "Pending: Revoke all permissions";
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private void set(PermissionItem item, boolean granted) {
        if (item.isMenu()) {
            if (granted) grantedMenuCodes.add(item.getCode());
            else grantedMenuCodes.remove(item.getCode());
        } else {
            if (granted) grantedActionCodes.add(item.getCode());
            else grantedActionCodes.remove(item.getCode());
        }
    }

    private boolean guard(PermissionItem item) {
        if (!authorized) { deny("Access denied — the PERM_ADMIN_ACCESS permission is required."); return false; }
        if (editingTarget == EditingTarget.ROLE && selectedRole == null) { deny("No role selected."); return false; }
        if (editingTarget == EditingTarget.USER && selectedUserId == null) { deny("No user selected."); return false; }
        return true;
    }

    private void deny(String message) {
        statusMessage = message;
        Clients.showNotification(message, Clients.NOTIFICATION_TYPE_ERROR, null,
                "top_center", 4000);
    }

    private RolePermissions findRole(String roleCode) {
        return roles.stream()
                .filter(r -> r.getRoleCode().equals(roleCode))
                .findFirst()
                .orElse(null);
    }

    private void applySelection(RolePermissions role) {
        selectedRole = role;
        refreshRoleCounts();
    }

    private void resync() {
        if (editingTarget == EditingTarget.USER && selectedUserId != null) {
            loadUserOverrides(selectedUserId);
        } else if (selectedRole != null) {
            PermissionMatrix matrix = permissionService.loadMatrix();
            roles = matrix.getRoles();
            RolePermissions refreshed = findRole(selectedRole.getRoleCode());
            if (refreshed == null) return;
            grantedMenuCodes = refreshed.getMenuCodes();
            grantedActionCodes = refreshed.getActionCodes();
        }
    }

    public boolean isLocked(PermissionItem item) {
        if (editingTarget == EditingTarget.USER) {
            // For user editing, check if the user's role is SUPER_ADMIN
            if (selectedUserId == null) return false;
            String userRoleCode = getUserRoleCode(selectedUserId);
            return SUPER_ADMIN.equals(userRoleCode)
                    && PROTECTED_FOR_SUPER_ADMIN.contains(item.getCode());
        }
        return SUPER_ADMIN.equals(selectedRole.getRoleCode())
                && PROTECTED_FOR_SUPER_ADMIN.contains(item.getCode());
    }

    /** Returns the role code for a given user ID (used for locked permission checks). */
    private String getUserRoleCode(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getRoleCode)
                .orElse(null);
    }

    private void persist(List<Long> menus, List<Long> actions, String reason) {
        try {
            permissionService.replacePermissions(selectedRole.getRoleCode(), menus, actions);
            reload();
            statusMessage = reason + " for " + selectedRole.getRoleName();
            Clients.showNotification(statusMessage, Clients.NOTIFICATION_TYPE_INFO,
                    null, "top_center", 3000);
        } catch (RuntimeException ex) {
            failBulk(reason, ex);
        }
    }

    private void persistUser(Long userId, List<Long> menus, List<Long> actions, String reason) {
        try {
            permissionService.replaceUserPermissions(userId, menus, actions);
            loadUserOverrides(userId);
            accessControlService.invalidate();
            statusMessage = reason + " — saved to database.";
            Clients.showNotification(statusMessage, Clients.NOTIFICATION_TYPE_INFO,
                    null, "top_center", 3000);
        } catch (RuntimeException ex) {
            failBulk(reason, ex);
        }
    }

    private boolean guardAll() {
        if (!authorized) {
            deny("Access denied — the PERM_ADMIN_ACCESS permission is required.");
            return false;
        }
        if (editingTarget == EditingTarget.USER) {
            // Allow bulk operations on user overrides (even for SUPER_ADMIN users)
            return true;
        }
        if (SUPER_ADMIN.equals(selectedRole.getRoleCode())) {
            deny("The SUPER_ADMIN role cannot be fully wiped — it would lock every "
                    + "administrator out of the portal. Only protected permissions are "
                    + "revoked.");
            return false;
        }
        return true;
    }

    private List<Long> allMenuIds() {
        return menuItems.stream().filter(PermissionItem::isMenu)
                .map(PermissionItem::getId).collect(Collectors.toList());
    }

    private List<Long> allActionIds() {
        return actionItems.stream().filter(item -> !item.isMenu())
                .map(PermissionItem::getId).collect(Collectors.toList());
    }

    private List<Long> retainedMenuIds() { return allMenuIds(); }
    private List<Long> retainedActionIds() { return allActionIds(); }

    private void refreshRoleCounts() {
        if (editingTarget == EditingTarget.USER) {
            // For user editing, counts reflect the user overrides only
            return;
        }
        if (selectedRole == null || menuItems.isEmpty()) return;
        RolePermissions snap = findRole(selectedRole.getRoleCode());
        if (snap != null) {
            grantedMenuCodes = snap.getMenuCodes();
            grantedActionCodes = snap.getActionCodes();
        }
    }

    /** Replaces the in-memory role snapshot with a fresh copy read from the database,
     *  so that grantedMenuCodes / grantedActionCodes reflect the just-saved state. */
    private void updateRoleSnapshot(RolePermissions snap) {
        if (snap == null) return;
        selectedRole = snap;
        grantedMenuCodes = new LinkedHashSet<>(snap.getMenuCodes());
        grantedActionCodes = new LinkedHashSet<>(snap.getActionCodes());
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i).getRoleCode().equals(snap.getRoleCode())) {
                roles.set(i, snap);
                break;
            }
        }
    }

    private void notify(String code, boolean granted) {
        // status bar is updated via statusMessage; nothing else required.
    }

    // ---- ZK-bound state properties ----

    public boolean isAuthorized() { return authorized; }

    public String getSelectedRoleBadgeClass() {
        if (selectedRole == null) return "badge badge-secondary";
        return selectedRole.getGrantedTotal() >= getPermissionTotal()
                ? "badge badge-success" : "badge badge-info";
    }

    public String getSelectedRoleCode() { return selectedRole != null ? selectedRole.getRoleCode() : ""; }
    public String getSelectedRoleName() { return selectedRole != null ? selectedRole.getRoleName() : ""; }
    public String getSelectedRoleDescription() {
        return selectedRole != null ? selectedRole.getDescription() : "";
    }

    public String getMenuProgressText()   { return getMenuGrantedCount() + " / " + getMenuTotal(); }
    public String getActionProgressText() { return getActionGrantedCount() + " / " + getActionTotal(); }
    public int    getGrantedTotal()       { return getMenuGrantedCount() + getActionGrantedCount(); }
    public String getCompletionStyle() {
        if (getPermissionTotal() == 0) return "width:0%";
        double pct = getGrantedTotal() * 100.0 / getPermissionTotal();
        return String.format(Locale.ROOT, "width:%.1f%%", Math.min(pct, 100.0));
    }
    public String getCompletionText() {
        return getGrantedTotal() + " of " + getPermissionTotal() + " permissions active";
    }

    public int getMenuTotal()     { return menuItems.size(); }
    public int getActionTotal()   { return actionItems.size(); }
    public int getPermissionTotal() { return menuItems.size() + actionItems.size(); }
    public int getMenuGrantedCount()   { return grantedMenuCodes.size(); }
    public int getActionGrantedCount() { return grantedActionCodes.size(); }

    public boolean isGranted(PermissionItem item) {
        // grantedMenuCodes / grantedActionCodes always hold the state that is
        // currently being edited: the role's codes in role mode, and the
        // user's effective codes (role inheritance + grants - denies) in
        // user mode. Toggling a switch updates these sets in memory, so a
        // plain membership test is the effective state.
        return item.isMenu() ? grantedMenuCodes.contains(item.getCode())
                             : grantedActionCodes.contains(item.getCode());
    }

    /** True when the given menu code is currently granted (role or user override).
     *  Used to dim the action groups whose parent module is switched off. */
    public boolean isMenuGranted(String menuCode) {
        if (menuCode == null) return false;
        return grantedMenuCodes.contains(menuCode);
    }

    public boolean isProtected(PermissionItem item) {
        return SUPER_ADMIN.equals(selectedRole.getRoleCode())
                && PROTECTED_FOR_SUPER_ADMIN.contains(item.getCode());
    }

    public String getLockHint() {
        return "Protected: cannot be revoked from " + SUPER_ADMIN
                + " — it would lock every administrator out of the portal.";
    }

    /** Text shown under the Members card title: 'N in <role>'. */
    public String getMemberCountText() {
        String role = selectedRole != null ? selectedRole.getRoleName() : "(none)";
        return roleUsers.size() + " in " + role;
    }

    public String getStatusMessage() { return statusMessage; }

    /** Number of pending unsaved changes. */
    public int getPendingChangesCount() { return pendingChanges.size(); }

    /** Formatted string for pending changes count. */
    public String getPendingChangesCountStr() {
        return pendingChanges.size() + " pending change" + (pendingChanges.size() == 1 ? "" : "s");
    }

    /** Whether a save operation is in progress. */
    public boolean isSaving() { return saving; }

    /** Save result message for UI feedback. */
    public String getSaveMessage() { return saveMessage; }
    public List<UserItem> getRoleUsers() { return roleUsers; }
    public List<UserItem> getFilteredRoleUsers() { return filteredRoleUsers; }
    public List<PermissionItem> getMenuItems() { return menuItems; }
    public List<PermissionItem> getActionItems() { return actionItems; }
    public List<SelectionItem> getRoleOptions() { return selectionItems; }
    public List<ActionModuleGroup> getActionModuleGroups() { return actionModuleGroups; }

    // ---- Role rail helpers (original API, kept for the card layout) ----

    public String getRoleCardClass(RolePermissions role) {
        if (role.equals(selectedRole)) return "perm-role-card perm-role-card-active";
        return "perm-role-card";
    }

    public String getRoleDescription(RolePermissions role) {
        int total = role.getGrantedTotal();
        int max = role.getGrantedMenuCount() + role.getGrantedActionCount();
        return role.getDescription() + " — " + total + " of " + max + " rights granted";
    }

    public String getRoleBadgeClassFor(RolePermissions role) {
        if (SUPER_ADMIN.equals(role.getRoleCode())) return "badge badge-warning";
        if (role.getGrantedTotal() == 0) return "badge badge-ghost";
        return "badge badge-primary";
    }

    public String getRoleSummaryText(RolePermissions role) {
        return role.getGrantedMenuCount() + " menus · "
                + role.getGrantedActionCount() + " actions";
    }

    // ---- New getters for user-scoped editing ----

    public boolean isEditingUser() { return editingTarget == EditingTarget.USER; }
    public boolean isEditingRole() { return editingTarget == EditingTarget.ROLE; }
    public boolean getEditingUser() { return isEditingUser(); }
    public boolean getEditingRole() { return isEditingRole(); }
    public Long getSelectedUserId() { return selectedUserId; }
    public EditingTarget getEditingTarget() { return editingTarget; }

    public String getEditingTargetLabel() {
        if (editingTarget == EditingTarget.USER && selectedUserId != null) {
            User user = roleUsers.stream()
                    .map(UserItem::getUser)
                    .filter(u -> u.getId().equals(selectedUserId))
                    .findFirst()
                    .orElse(null);
            if (user != null) return "User: " + user.getUsername();
            return "User: " + selectedUserId;
        }
        return selectedRole != null ? selectedRole.getRoleName() : "(none)";
    }

    public String getUserRowClass(UserItem userItem) {
        if (userItem == null || selectedUserId == null) {
            return "perm-user-row";
        }
        if (userItem.getUser() != null && selectedUserId.equals(userItem.getUser().getId())) {
            return "perm-user-row perm-user-row-selected";
        }
        return "perm-user-row";
    }

    // ---- CSS class helpers (ZK-safe: return the FULL class string) ----
    // ZK's binder does not evaluate "@load(...)" embedded inside literal
    // text, so conditional classes must be produced by a single method
    // call bound with a pure "@load(vm.xxxClass(...))".

    /** True when the given member row is the user currently being edited. */
    public boolean isMemberSelected(UserItem userItem) {
        return userItem != null && userItem.getUser() != null
                && selectedUserId != null
                && selectedUserId.equals(userItem.getUser().getId());
    }

    /** Full class string for a Members list row. */
    public String memberRowClass(UserItem userItem) {
        return isMemberSelected(userItem)
                ? "perm-member-row perm-member-row-selected"
                : "perm-member-row";
    }

    /** Full class string for a Role-or-user selector row. */
    public String selectorRowClass(SelectionItem item) {
        return isSelectorRowActive(item)
                ? "perm-selector-row perm-selector-row-active"
                : "perm-selector-row";
    }

    /** Full class string for a permission tile (menu or action). */
    public String tileClass(PermissionItem item) {
        StringBuilder sb = new StringBuilder("perm-tile");
        if (isGranted(item)) {
            sb.append(" perm-tile-on");
        }
        if (isLocked(item)) {
            sb.append(" perm-tile-locked");
        }
        return sb.toString();
    }

    /** Full class string for the checkbox inside a permission tile. */
    public String tileCheckClass(PermissionItem item) {
        return isGranted(item)
                ? "perm-tile-check perm-tile-check-on"
                : "perm-tile-check";
    }

    /** Full class string for a segmented-control button (ROLE / USER). */
    public String segBtnClass(String mode) {
        boolean active = mode != null
                && selectionMode != null
                && mode.equalsIgnoreCase(selectionMode.name());
        return active ? "perm-seg-btn perm-seg-active" : "perm-seg-btn";
    }

    /** Full class string for an action module group, dimmed when its parent menu is off. */
    public String moduleGroupClass(String menuCode) {
        return isMenuGranted(menuCode)
                ? "perm-module-group"
                : "perm-module-group perm-module-off";
    }

    // ---- Bulk operation internals ----

    private void bulk(boolean grantMenus, boolean grantActions) {
        if (!guardAll()) return;
        try {
            List<Long> menus = grantMenus ? allMenuIds() : retainedMenuIds();
            List<Long> actions = grantActions ? allActionIds() : retainedActionIds();
            permissionService.replacePermissions(selectedRole.getRoleCode(), menus, actions);
            reload();
            statusMessage = "Bulk change applied to " + selectedRole.getRoleName();
            Clients.showNotification(statusMessage, Clients.NOTIFICATION_TYPE_INFO,
                    null, "top_center", 2500);
        } catch (RuntimeException ex) {
            failBulk("bulk change", ex);
        }
    }

    private void failBulk(String reason, RuntimeException ex) {
        statusMessage = "Bulk operation failed: " + reason + " — " + ex.getMessage();
        Clients.showNotification(statusMessage, Clients.NOTIFICATION_TYPE_ERROR, null,
                "top_center", 4500);
        reload();
    }

    // ---- Action module grouping (action rights card) ----

    /**
     * A module group for the action rights card: a parent menu plus the
     * granular actions that belong to it. The group is dimmed when the
     * parent menu permission is switched off.
     */
    public static class ActionModuleGroup {
        private final PermissionItem menu;
        private final List<PermissionItem> actions;

        public ActionModuleGroup(PermissionItem menu, List<PermissionItem> actions) {
            this.menu = menu;
            this.actions = List.copyOf(actions);
        }

        public PermissionItem getMenu()        { return menu; }
        public List<PermissionItem> getActions() { return actions; }
        public String getMenuCode()            { return menu.getCode(); }
        public String getMenuLabel()           { return menu.getLabel(); }
        public String getMenuRoute()           { return menu.getRouteUrl(); }
    }

    /** Groups the action catalogue under their parent module menus. */
    private List<ActionModuleGroup> buildActionModuleGroups() {
        Map<String, List<PermissionItem>> byModule = new LinkedHashMap<>();
        for (PermissionItem action : actionItems) {
            String menuCode = menuCodeForAction(action.getCode());
            byModule.computeIfAbsent(menuCode, k -> new ArrayList<>()).add(action);
        }
        List<ActionModuleGroup> groups = new ArrayList<>();
        for (PermissionItem menu : menuItems) {
            List<PermissionItem> actions = byModule.get(menu.getCode());
            if (actions != null && !actions.isEmpty()) {
                groups.add(new ActionModuleGroup(menu, actions));
            }
        }
        return groups;
    }

    /** Maps an action code to the menu code of the module it belongs to. */
    private String menuCodeForAction(String actionCode) {
        if (actionCode == null) return "";
        if (actionCode.startsWith("PERM_ADMIN_")) return "MENU_PERM_ADMIN";
        if (actionCode.startsWith("CATALOG_"))    return "MENU_CATALOG";
        if (actionCode.startsWith("ITEM_"))       return "MENU_ITEM_CREATOR";
        if (actionCode.startsWith("STOCK_"))      return "MENU_STOCK_ADJUST";
        return "";
    }
}
