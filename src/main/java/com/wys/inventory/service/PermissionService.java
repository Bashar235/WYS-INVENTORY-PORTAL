package com.wys.inventory.service;

import com.wys.inventory.model.Action;
import com.wys.inventory.model.Menu;
import com.wys.inventory.model.Role;
import com.wys.inventory.model.User;
import com.wys.inventory.model.UserActionPermission;
import com.wys.inventory.model.UserActionPermissionId;
import com.wys.inventory.model.UserMenuPermission;
import com.wys.inventory.model.UserMenuPermissionId;
import com.wys.inventory.repository.ActionRepository;
import com.wys.inventory.repository.MenuRepository;
import com.wys.inventory.repository.RoleRepository;
import com.wys.inventory.repository.UserActionPermissionRepository;
import com.wys.inventory.repository.UserMenuPermissionRepository;
import com.wys.inventory.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
     * Transactional gateway for the RBAC permission matrix (role_menus / role_actions).
     *
     * Why this class exists
     * ---------------------
     * The permission page used to mutate {@code Role.menus} / {@code Role.actions} straight
     * from the ZK ViewModel. That could never persist, for three independent reasons:
     *
     *  1. The ViewModel is instantiated by ZK, not by Spring, so {@code @Transactional} on a
     *     {@code @Command} method was silently ignored.
     *  2. {@code roleRepository.save(...)} on an entity returned by a previous query executes
     *     {@code EntityManager.merge(...)} on a <b>detached</b> entity whose collections were
     *     also detached, so the join-table rows were never written.
     *  3. {@code Menu} / {@code Action} had identity-based {@code equals} / {@code hashCode}.
     *     Adding an instance loaded from another persistence context to a
     *     {@code HashSet<Menu>} created a duplicate element, which Hibernate then flushed as
     *     a second {@code role_menus} row and violated the {@code (role_id, menu_id)} primary key.
     *
     * Everything here runs inside a real Spring-managed transaction, loads the {@link Role}
     * as a <b>managed</b> entity, initialises the lazy collections while the transaction is
     * still open, mutates them, and flushes explicitly. Hibernate then writes exactly the
     * difference to the join tables.
     *
     * Reads never escape this class: {@link #loadMatrix()} materialises immutable snapshots
     * while the transaction is open, so the GUI can never touch a lazy proxy.
     */
    @Service
    public class PermissionService {

    /** The complete permission catalogue, resolved once per {@link #loadMatrix()} call. */
    private final RoleRepository   roleRepository;
    private final MenuRepository   menuRepository;
    private final ActionRepository actionRepository;
    private final UserRepository  userRepository;
    private final UserMenuPermissionRepository  userMenuPermissionRepository;
    private final UserActionPermissionRepository userActionPermissionRepository;

    public PermissionService(RoleRepository roleRepository,
                                 MenuRepository menuRepository,
                                 ActionRepository actionRepository,
                                 UserRepository userRepository,
                                 UserMenuPermissionRepository userMenuPermissionRepository,
                                 UserActionPermissionRepository userActionPermissionRepository) {
        this.roleRepository   = roleRepository;
        this.menuRepository   = menuRepository;
        this.actionRepository = actionRepository;
        this.userRepository  = userRepository;
        this.userMenuPermissionRepository  = userMenuPermissionRepository;
        this.userActionPermissionRepository = userActionPermissionRepository;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * Loads every role together with its granted permission codes, plus the full
     * catalogue of declarable menus and actions. All collections are initialised
     * inside the read-only transaction, so the returned snapshot is detached-safe.
     */
    @Transactional(readOnly = true)
    public PermissionMatrix loadMatrix() {
        List<PermissionItem> menus = menuRepository.findAll().stream()
                .sorted(Comparator.comparing(Menu::getDisplayOrder,
                             Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Menu::getMenuCode))
                .map(PermissionItem::ofMenu)
                .collect(Collectors.toList());

        List<PermissionItem> actions = actionRepository.findAll().stream()
                .sorted(Comparator.comparing(Action::getActionCode))
                .map(PermissionItem::ofAction)
                .collect(Collectors.toList());

        List<RolePermissions> roles = roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getRoleName))
                .map(this::snapshot)
                .collect(Collectors.toList());

        return new PermissionMatrix(menus, actions, roles);
    }

    /** Reads one role's grants into a detached-safe snapshot. */
    @Transactional(readOnly = true)
    public RolePermissions loadRole(String roleCode) {
        Role role = requireRole(roleCode);
        return snapshot(role);
    }

    // ------------------------------------------------------------------
    // Writes  1role
    // ------------------------------------------------------------------

    /**
     * Grants or revokes a single menu for the given role and flushes the join table.
     *
     * @return {@code true} when the permission actually changed (i.e. something was written)
     */
    @Transactional
    public boolean setMenuGranted(String roleCode, Long menuId, boolean granted) {
        Role role = requireRole(roleCode);
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu id: " + menuId));

        // Touch the lazy set while the transaction is open, otherwise Hibernate cannot
        // compute the collection diff at flush time.
        Set<Menu> menus = role.getMenus();
        boolean changed = granted ? menus.add(menu) : menus.remove(menu);

        roleRepository.flush();
        return changed;
    }

    /**
     * Grants or revokes a single action for the given role and flushes the join table.
     *
     * @return {@code true} when the permission actually changed (i.e. something was written)
     */
    @Transactional
    public boolean setActionGranted(String roleCode, Long actionId, boolean granted) {
        Role role = requireRole(roleCode);
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown action id: " + actionId));

        Set<Action> actions = role.getActions();
        boolean changed = granted ? actions.add(action) : actions.remove(action);

        roleRepository.flush();
        return changed;
    }

    /**
     * Bulk-synchronises a whole role: every menu/action not present in the supplied sets is
     * revoked, every one present is granted. This is what the explicit "Save Permissions"
     * button calls, so the full matrix is persisted in a single transaction.
     *
     * @return a fresh snapshot of the role as it now stands in the database
     */
    @Transactional
    public RolePermissions replacePermissions(String roleCode,
                                              Collection<Long> menuIds,
                                              Collection<Long> actionIds) {
        Role role = requireRole(roleCode);
        Set<Menu> targetMenus = new LinkedHashSet<>(
                menuRepository.findAllById(menuIds == null ? List.of() : menuIds));
        Set<Action> targetActions = new LinkedHashSet<>(
                actionRepository.findAllById(actionIds == null ? List.of() : actionIds));

        Set<Menu> menus = role.getMenus();
        menus.retainAll(targetMenus);
        menus.addAll(targetMenus);

        Set<Action> actions = role.getActions();
        actions.retainAll(targetActions);
        actions.addAll(targetActions);

        roleRepository.flush();

        return snapshot(requireRole(roleCode));
    }

    // ------------------------------------------------------------------
    // User-level permission overrides (user_menus / user_actions)
    // ------------------------------------------------------------------

    /**
     * Grants or revokes a single menu for a specific user via the user_menus
     * table. A {@code granted = TRUE} row is an explicit grant that adds a
     * right the role lacks; a {@code granted = FALSE} row is an explicit deny
     * that removes a right the role would otherwise grant. The deny always
     * wins over the role inheritance at runtime.
     *
     * @return {@code true} when the stored override actually changed
     */
    @Transactional
    public boolean setUserMenuGranted(Long userId, Long menuId, boolean granted) {
        User user = requireUser(userId);
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu id: " + menuId));
        return upsertMenuOverride(user.getId(), menu.getId(), granted);
    }

    /**
     * Grants or revokes a single action for a specific user via the
     * user_actions table. See {@link #setUserMenuGranted} for the
     * grant/deny semantics.
     *
     * @return {@code true} when the stored override actually changed
     */
    @Transactional
    public boolean setUserActionGranted(Long userId, Long actionId, boolean granted) {
        User user = requireUser(userId);
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown action id: " + actionId));
        return upsertActionOverride(user.getId(), action.getId(), granted);
    }

    /**
     * Loads a user's <b>effective</b> permission codes (menus and actions)
     * into a detached-safe snapshot. Effective = role inheritance, plus
     * explicit grants, minus explicit denies.
     */
    @Transactional(readOnly = true)
    public UserPermissions loadUserPermissions(Long userId) {
        User user = requireUser(userId);
        return snapshotUser(user);
    }

    /**
     * Bulk-synchronises a user's permission overrides so that the user's
     * <b>effective</b> menu/action rights match exactly the supplied sets.
     *
     * <p>For every permission in the catalogue the service compares the
     * desired state with the role inheritance:
     * <ul>
     *   <li>desired == role-inherited  -> no override row is needed (any
     *       existing row is deleted, so the user falls back to the role);</li>
     *   <li>desired != role-inherited  -> an override row is written with
     *       {@code granted = desired} (a grant when the role lacks it, a
     *       deny when the role has it).</li>
     * </ul>
     *
     * @param userId    the target user
     * @param menuIds   the full set of menu ids the user should effectively have
     * @param actionIds the full set of action ids the user should effectively have
     * @return a fresh snapshot of the user's effective rights as they now stand
     */
    @Transactional
    public UserPermissions replaceUserPermissions(Long userId,
                                                    Collection<Long> menuIds,
                                                    Collection<Long> actionIds) {
        User user = requireUser(userId);

        Map<Long, String> menuCodeMap   = menuCodeMap();
        Map<Long, String> actionCodeMap = actionCodeMap();

        Set<String> desiredMenuCodes   = codesFromIds(menuIds, menuCodeMap);
        Set<String> desiredActionCodes = codesFromIds(actionIds, actionCodeMap);

        Role role = user.getRole();
        Set<String> roleMenuCodes   = roleMenuCodes(role);
        Set<String> roleActionCodes = roleActionCodes(role);

        syncMenuOverrides(user.getId(), menuCodeMap, desiredMenuCodes, roleMenuCodes);
        syncActionOverrides(user.getId(), actionCodeMap, desiredActionCodes, roleActionCodes);

        userRepository.flush();

        return snapshotUser(requireUser(userId));
    }

    /**
     * Returns the number of users assigned to the given role code.
     */
    @Transactional(readOnly = true)
    public long countUsersInRole(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return 0;
        }
        return userRepository.countByRole_RoleCode(roleCode.trim());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private Role requireRole(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("roleCode is required");
        }
        return roleRepository.findByRoleCode(roleCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleCode));
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id: " + userId));
    }

    /** Converts a managed role into a detached-safe snapshot. Call inside a transaction. */
    private RolePermissions snapshot(Role role) {
        Set<String> menuCodes = new LinkedHashSet<>();
        for (Menu m : role.getMenus()) {
            menuCodes.add(m.getMenuCode());
        }
        Set<String> actionCodes = new LinkedHashSet<>();
        for (Action a : role.getActions()) {
            actionCodes.add(a.getActionCode());
        }
        return new RolePermissions(role.getId(), role.getRoleCode(), role.getRoleName(),
                role.getDescription(), menuCodes, actionCodes);
    }

    /**
     * Converts a managed user into a detached-safe snapshot of the user's
     * <b>effective</b> rights: role inheritance, plus explicit grants,
     * minus explicit denies. Call inside a transaction.
     */
    private UserPermissions snapshotUser(User user) {
        Role role = user.getRole();
        Set<String> roleMenuCodes   = roleMenuCodes(role);
        Set<String> roleActionCodes = roleActionCodes(role);

        Set<String> menuCodes   = new LinkedHashSet<>(roleMenuCodes);
        Set<String> actionCodes = new LinkedHashSet<>(roleActionCodes);

        Map<Long, String> menuCodeMap   = menuCodeMap();
        Map<Long, String> actionCodeMap = actionCodeMap();

        for (UserMenuPermission o : userMenuPermissionRepository.findByUserId(user.getId())) {
            String code = menuCodeMap.get(o.getMenuId());
            if (code == null) continue;
            if (o.isGranted()) {
                menuCodes.add(code);
            } else {
                menuCodes.remove(code);
            }
        }
        for (UserActionPermission o : userActionPermissionRepository.findByUserId(user.getId())) {
            String code = actionCodeMap.get(o.getActionId());
            if (code == null) continue;
            if (o.isGranted()) {
                actionCodes.add(code);
            } else {
                actionCodes.remove(code);
            }
        }

        return new UserPermissions(user.getId(), user.getUsername(),
                role != null ? role.getRoleCode() : null,
                menuCodes, actionCodes);
    }

    // ---- User-override helpers ----

    /** Upserts a single user_menu override row. Returns true when it changed. */
    private boolean upsertMenuOverride(Long userId, Long menuId, boolean granted) {
        return userMenuPermissionRepository
                .findById(new UserMenuPermissionId(userId, menuId))
                .map(existing -> {
                    if (existing.isGranted() == granted) {
                        return false;
                    }
                    existing.setGranted(granted);
                    userMenuPermissionRepository.save(existing);
                    return true;
                })
                .orElseGet(() -> {
                    userMenuPermissionRepository.save(
                            new UserMenuPermission(userId, menuId, granted));
                    return true;
                });
    }

    /** Upserts a single user_action override row. Returns true when it changed. */
    private boolean upsertActionOverride(Long userId, Long actionId, boolean granted) {
        return userActionPermissionRepository
                .findById(new UserActionPermissionId(userId, actionId))
                .map(existing -> {
                    if (existing.isGranted() == granted) {
                        return false;
                    }
                    existing.setGranted(granted);
                    userActionPermissionRepository.save(existing);
                    return true;
                })
                .orElseGet(() -> {
                    userActionPermissionRepository.save(
                            new UserActionPermission(userId, actionId, granted));
                    return true;
                });
    }

    /**
     * Reconciles the user_menu rows with the desired effective state:
     * an override row exists exactly when the desired state differs from
     * the role inheritance.
     */
    private void syncMenuOverrides(Long userId,
                                   Map<Long, String> menuCodeMap,
                                   Set<String> desiredMenuCodes,
                                   Set<String> roleMenuCodes) {
        Map<Long, UserMenuPermission> existing = userMenuPermissionRepository
                .findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        UserMenuPermission::getMenuId, o -> o, (a, b) -> a));

        for (Map.Entry<Long, String> entry : menuCodeMap.entrySet()) {
            Long menuId = entry.getKey();
            String code = entry.getValue();
            boolean roleHas  = roleMenuCodes.contains(code);
            boolean desired  = desiredMenuCodes.contains(code);
            UserMenuPermission row = existing.get(menuId);

            if (roleHas == desired) {
                // Fall back to the role inheritance — no override needed.
                if (row != null) {
                    userMenuPermissionRepository.delete(row);
                }
            } else if (row != null) {
                row.setGranted(desired);
                userMenuPermissionRepository.save(row);
            } else {
                userMenuPermissionRepository.save(
                        new UserMenuPermission(userId, menuId, desired));
            }
        }
    }

    /** Same as {@link #syncMenuOverrides} for the user_actions table. */
    private void syncActionOverrides(Long userId,
                                     Map<Long, String> actionCodeMap,
                                     Set<String> desiredActionCodes,
                                     Set<String> roleActionCodes) {
        Map<Long, UserActionPermission> existing = userActionPermissionRepository
                .findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        UserActionPermission::getActionId, o -> o, (a, b) -> a));

        for (Map.Entry<Long, String> entry : actionCodeMap.entrySet()) {
            Long actionId = entry.getKey();
            String code = entry.getValue();
            boolean roleHas  = roleActionCodes.contains(code);
            boolean desired  = desiredActionCodes.contains(code);
            UserActionPermission row = existing.get(actionId);

            if (roleHas == desired) {
                if (row != null) {
                    userActionPermissionRepository.delete(row);
                }
            } else if (row != null) {
                row.setGranted(desired);
                userActionPermissionRepository.save(row);
            } else {
                userActionPermissionRepository.save(
                        new UserActionPermission(userId, actionId, desired));
            }
        }
    }

    /** id -> menuCode map for the whole menu catalogue. */
    private Map<Long, String> menuCodeMap() {
        return menuRepository.findAll().stream()
                .collect(Collectors.toMap(Menu::getId, Menu::getMenuCode));
    }

    /** id -> actionCode map for the whole action catalogue. */
    private Map<Long, String> actionCodeMap() {
        return actionRepository.findAll().stream()
                .collect(Collectors.toMap(Action::getId, Action::getActionCode));
    }

    /** Resolves a collection of ids to their codes, skipping unknown ids. */
    private Set<String> codesFromIds(Collection<Long> ids,
                                     Map<Long, String> codeMap) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Long id : ids) {
            String code = codeMap.get(id);
            if (code != null) {
                codes.add(code);
            }
        }
        return codes;
    }

    /** The role's inherited menu codes (empty when the user has no role). */
    private Set<String> roleMenuCodes(Role role) {
        if (role == null) {
            return Collections.emptySet();
        }
        return role.getMenus().stream()
                .map(Menu::getMenuCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The role's inherited action codes (empty when the user has no role). */
    private Set<String> roleActionCodes(Role role) {
        if (role == null) {
            return Collections.emptySet();
        }
        return role.getActions().stream()
                .map(Action::getActionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ==================================================================
    // Immutable view models handed to the ZK layer
    // ==================================================================

    /** Full snapshot of the permission system: catalogue + every role's grants. */
    public static class PermissionMatrix {
        private final List<PermissionItem> menus;
        private final List<PermissionItem> actions;
        private final List<RolePermissions> roles;

        public PermissionMatrix(List<PermissionItem> menus,
                                List<PermissionItem> actions,
                                List<RolePermissions> roles) {
            this.menus   = Collections.unmodifiableList(new ArrayList<>(menus));
            this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
            this.roles   = Collections.unmodifiableList(new ArrayList<>(roles));
        }

        public List<PermissionItem> getMenus()   { return menus; }
        public List<PermissionItem> getActions() { return actions; }
        public List<RolePermissions> getRoles()  { return roles; }
    }

    /** One role and the permission codes currently granted to it. */
    public static class RolePermissions {
        private final Long   id;
        private final String roleCode;
        private final String roleName;
        private final String description;
        private final Set<String> menuCodes;
        private final Set<String> actionCodes;

        public RolePermissions(Long id, String roleCode, String roleName, String description,
                               Set<String> menuCodes, Set<String> actionCodes) {
            this.id          = id;
            this.roleCode    = roleCode;
            this.roleName    = roleName;
            this.description = description;
            this.menuCodes   = new LinkedHashSet<>(menuCodes);
            this.actionCodes = new LinkedHashSet<>(actionCodes);
        }

        public Long   getId()          { return id; }
        public String getRoleCode()    { return roleCode; }
        public String getRoleName()    { return roleName; }
        public String getDescription() { return description; }
        public Set<String> getMenuCodes()   { return menuCodes; }
        public Set<String> getActionCodes() { return actionCodes; }

        public boolean has(String permissionCode) {
            return menuCodes.contains(permissionCode) || actionCodes.contains(permissionCode);
        }

        public int getGrantedMenuCount()   { return menuCodes.size(); }
        public int getGrantedActionCount() { return actionCodes.size(); }
        public int getGrantedTotal()       { return menuCodes.size() + actionCodes.size(); }

        @Override public String toString() { return roleCode; }
    }

    /**
     * One user and their <b>effective</b> permission codes: the role's
     * inherited rights, plus explicit user grants, minus explicit user
     * denies (user_menus / user_actions).
     */
    public static class UserPermissions {
        private final Long   id;
        private final String username;
        private final String roleCode;
        private final Set<String> menuCodes;
        private final Set<String> actionCodes;

        public UserPermissions(Long id, String username, String roleCode,
                               Set<String> menuCodes, Set<String> actionCodes) {
            this.id          = id;
            this.username    = username;
            this.roleCode    = roleCode;
            this.menuCodes   = new LinkedHashSet<>(menuCodes);
            this.actionCodes = new LinkedHashSet<>(actionCodes);
        }

        public Long   getId()          { return id; }
        public String getUsername()    { return username; }
        public String getRoleCode()    { return roleCode; }
        public Set<String> getMenuCodes()   { return menuCodes; }
        public Set<String> getActionCodes() { return actionCodes; }

        public int getGrantedMenuCount()   { return menuCodes.size(); }
        public int getGrantedActionCount() { return actionCodes.size(); }
        public int getGrantedTotal()       { return menuCodes.size() + actionCodes.size(); }

        @Override public String toString() { return username; }
    }

    /**
     * A single declarable permission rendered as one GUI row.
     * Covers both the MENU_* keys and the granular action keys.
     */
    public static class PermissionItem {
        public enum Kind { MENU, ACTION }

        private final Long   id;
        private final String code;
        private final String label;
        private final String description;
        private final Kind   kind;
        private final String routeUrl;
        private final String icon;
        private final Integer displayOrder;

        private PermissionItem(Long id, String code, String label, String description,
                               Kind kind, String routeUrl, String icon, Integer displayOrder) {
            this.id          = id;
            this.code        = code;
            this.label       = label;
            this.description = description;
            this.kind        = kind;
            this.routeUrl    = routeUrl;
            this.icon        = icon;
            this.displayOrder = displayOrder;
        }

        public static PermissionItem ofMenu(Menu m) {
            return new PermissionItem(m.getId(), m.getMenuCode(), m.getLabel(), null,
                    Kind.MENU, m.getRouteUrl(), m.getIcon(), m.getDisplayOrder());
        }

        public static PermissionItem ofAction(Action a) {
            return new PermissionItem(a.getId(), a.getActionCode(), a.getActionName(),
                    a.getDescription(), Kind.ACTION, null, null, null);
        }

        public Long    getId()           { return id; }
        public String  getCode()         { return code; }
        public String  getLabel()        { return label; }
        public String  getDescription()  { return description; }
        public Kind    getKind()         { return kind; }
        public String  getRouteUrl()     { return routeUrl; }
        public String  getIcon()         { return icon; }
        public Integer getDisplayOrder() { return displayOrder; }

        public boolean isMenu()   { return kind == Kind.MENU; }
        public boolean isAction() { return kind == Kind.ACTION; }

        /** Short human description, never null, for tooltips and secondary lines. */
        public String getHint() {
            if (description != null && !description.trim().isEmpty()) return description;
            if (kind == Kind.MENU) {
                return routeUrl == null ? "Navigation module" : "Opens " + routeUrl;
            }
            return "Operational capability";
        }

        /** CSS class for the leading glyph of the row. */
        public String getGlyphClass() {
            return kind == Kind.MENU ? "perm-row-icon perm-row-icon-menu"
                                     : "perm-row-icon perm-row-icon-action";
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PermissionItem other)) return false;
            return Objects.equals(code, other.code);
        }

        @Override public int hashCode() { return Objects.hashCode(code); }

        @Override public String toString() { return code; }
    }
}
