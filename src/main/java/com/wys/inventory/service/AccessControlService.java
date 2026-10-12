package com.wys.inventory.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import com.wys.inventory.model.Action;
import com.wys.inventory.model.Menu;
import com.wys.inventory.model.Role;
import com.wys.inventory.model.User;
import com.wys.inventory.model.UserActionPermission;
import com.wys.inventory.model.UserMenuPermission;
import com.wys.inventory.repository.ActionRepository;
import com.wys.inventory.repository.MenuRepository;
import com.wys.inventory.repository.RoleRepository;
import com.wys.inventory.repository.UserActionPermissionRepository;
import com.wys.inventory.repository.UserMenuPermissionRepository;
import com.wys.inventory.repository.UserRepository;

import jakarta.annotation.PreDestroy;

/**
 * Session-scoped service that loads a user's effective menu codes and
 * action codes once per login session and caches them in memory.
 *
 * Usage in ViewModels:
 *   @WireVariable private AccessControlService acs;
 *   boolean canEdit = acs.canDo("CATALOG_DOUBLECLICK_EDIT");
 *   boolean hasMenu = acs.canAccess("MENU_ITEM_CREATOR");
 *
 * Permission resolution order (effective rights):
 *
 *   effective = (roleHas && !denied) || explicitlyGranted
 *
 *   1. Start from the role's inherited permissions (role_menus / role_actions).
 *   2. Apply user-specific explicit grants (user_menus / user_actions with
 *      granted = TRUE) — these add rights the role does not have.
 *   3. Apply user-specific explicit denies (user_menus / user_actions with
 *      granted = FALSE) — these revoke rights the role would otherwise
 *      grant. A deny always wins over the role inheritance.
 *
 * The user_menus / user_actions tables carry a {@code granted} column
 * (TRUE = grant, FALSE = explicit revoke). It is mapped by the
 * {@link UserMenuPermission} / {@link UserActionPermission} entities, so a
 * Super Admin can both extend and restrict a single user's rights without
 * touching the role itself.
 *
 * Role.menus and Role.actions are LAZY collections. Accessing them outside
 * a JPA transaction causes LazyInitializationException. To avoid this, we
 * use RoleRepository.findByRoleCodeWithPermissions() which eagerly loads both
 * collections in a single JPQL JOIN FETCH query before caching them here.
 */
@Service
@SessionScope
public class AccessControlService {

    /**
     * Registry of the live, session-scoped instances keyed by username.
     * Lets a Super Admin invalidate another user's cached rights after
     * editing that user's permissions, so the change takes effect for the
     * target user's already-open session without a re-login.
     */
    private static final Map<String, AccessControlService> ACTIVE_INSTANCES =
            new HashMap<>();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final ActionRepository actionRepository;
    private final UserMenuPermissionRepository userMenuPermissionRepository;
    private final UserActionPermissionRepository userActionPermissionRepository;

    /** Cached set of menu codes the current user is allowed to see. */
    private Set<String> allowedMenuCodes  = null;

    /** Cached set of action codes the current user is allowed to perform. */
    private Set<String> allowedActionCodes = null;

    /** Ordered list of Menu objects for sidebar rendering. */
    private List<Menu> allowedMenus = null;

    /** The resolved role code (e.g. "ADMIN", "MANAGER") for display purposes. */
    private String roleCode = null;

    /** The username this instance is bound to (for registry cleanup). */
    private String registeredUsername;

    public AccessControlService(UserRepository userRepository,
                                RoleRepository roleRepository,
                                MenuRepository menuRepository,
                                ActionRepository actionRepository,
                                UserMenuPermissionRepository userMenuPermissionRepository,
                                UserActionPermissionRepository userActionPermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
        this.actionRepository = actionRepository;
        this.userMenuPermissionRepository = userMenuPermissionRepository;
        this.userActionPermissionRepository = userActionPermissionRepository;
    }

    // ---- Private initialization ----

    private void ensureLoaded() {
        if (allowedMenuCodes != null) return;   // already loaded this session

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            allowedMenuCodes   = Collections.emptySet();
            allowedActionCodes = Collections.emptySet();
            allowedMenus       = Collections.emptyList();
            roleCode           = null;
            return;
        }

        String username = auth.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            allowedMenuCodes   = Collections.emptySet();
            allowedActionCodes = Collections.emptySet();
            allowedMenus       = Collections.emptyList();
            roleCode           = null;
            return;
        }

        // user.getRole() is EAGER (FetchType.EAGER on the @ManyToOne), so it's safe.
        // However role.getMenus() and role.getActions() are LAZY sets — we must NOT
        // access them here via user.getRole().getMenus(). Instead we re-fetch the role
        // with an explicit JOIN FETCH query so the collections are initialized in one go.
        String userRoleCode = userOpt.get().getRoleCode();
        if (userRoleCode == null) {
            allowedMenuCodes   = Collections.emptySet();
            allowedActionCodes = Collections.emptySet();
            allowedMenus       = Collections.emptyList();
            roleCode           = null;
            return;
        }

        Optional<Role> roleOpt = roleRepository.findByRoleCodeWithPermissions(userRoleCode);
        if (roleOpt.isEmpty()) {
            allowedMenuCodes   = Collections.emptySet();
            allowedActionCodes = Collections.emptySet();
            allowedMenus       = Collections.emptyList();
            roleCode           = null;
            return;
        }

        Role role = roleOpt.get();
        roleCode = role.getRoleCode();

        // 1. Start with role-based permissions
        Set<String> menuCodes = new LinkedHashSet<>(role.getMenus().stream()
                .map(Menu::getMenuCode)
                .collect(Collectors.toSet()));
        Set<String> actionCodes = new LinkedHashSet<>(role.getActions().stream()
                .map(Action::getActionCode)
                .collect(Collectors.toSet()));

        // 2. Apply user-specific overrides on top of the role inheritance.
        //    granted = TRUE  -> explicit grant (adds a right the role lacks)
        //    granted = FALSE -> explicit deny  (removes a right the role grants)
        User user = userOpt.get();
        for (UserMenuPermission o : userMenuPermissionRepository.findByUserId(user.getId())) {
            String code = menuCodeOf(o.getMenuId());
            if (code == null) continue;
            if (o.isGranted()) {
                menuCodes.add(code);
            } else {
                menuCodes.remove(code);
            }
        }
        for (UserActionPermission o : userActionPermissionRepository.findByUserId(user.getId())) {
            String code = actionCodeOf(o.getActionId());
            if (code == null) continue;
            if (o.isGranted()) {
                actionCodes.add(code);
            } else {
                actionCodes.remove(code);
            }
        }

        allowedMenuCodes = menuCodes;
        allowedActionCodes = actionCodes;

        // Sidebar: every catalogue menu the user effectively has access to,
        // in display order. This reflects both grants and denies.
        allowedMenus = menuRepository.findAll().stream()
                .filter(m -> allowedMenuCodes.contains(m.getMenuCode()))
                .sorted(Comparator.comparing(Menu::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // Register this live instance so a Super Admin can invalidate it
        // after editing this user's permissions.
        registeredUsername = username;
        synchronized (ACTIVE_INSTANCES) {
            ACTIVE_INSTANCES.put(username, this);
        }
    }

    private String menuCodeOf(Long menuId) {
        if (menuId == null) return null;
        return menuRepository.findById(menuId)
                .map(Menu::getMenuCode)
                .orElse(null);
    }

    private String actionCodeOf(Long actionId) {
        if (actionId == null) return null;
        return actionRepository.findById(actionId)
                .map(Action::getActionCode)
                .orElse(null);
    }

    // ---- Public API ----

    /** Returns true if the current user's role is allowed to see the given menu. */
    public boolean canAccess(String menuCode) {
        ensureLoaded();
        return allowedMenuCodes.contains(menuCode);
    }

    /** Returns true if the current user's role is allowed to perform the given action. */
    public boolean canDo(String actionCode) {
        ensureLoaded();
        return allowedActionCodes.contains(actionCode);
    }

    /** Returns ordered list of menus for dynamic sidebar rendering. */
    public List<Menu> getAllowedMenus() {
        ensureLoaded();
        return allowedMenus;
    }

    /** Returns the role code string (e.g. "ADMIN", "MANAGER"). */
    public String getRoleCode() {
        ensureLoaded();
        return roleCode != null ? roleCode : "USER";
    }

    /** Invalidates the cached permissions (call on role change). */
    public void invalidate() {
        allowedMenuCodes   = null;
        allowedActionCodes = null;
        allowedMenus       = null;
        roleCode           = null;
    }

    /**
     * Invalidates the cached permissions of another user's live session.
     * Called by the permission admin after saving per-user overrides so the
     * target user's already-open session picks up the change immediately
     * instead of waiting for a re-login.
     */
    public static void invalidateForUser(String username) {
        if (username == null) return;
        AccessControlService instance;
        synchronized (ACTIVE_INSTANCES) {
            instance = ACTIVE_INSTANCES.get(username);
        }
        if (instance != null) {
            instance.invalidate();
        }
    }

    /** Removes this instance from the live-session registry on session destroy. */
    @PreDestroy
    public void destroy() {
        if (registeredUsername != null) {
            synchronized (ACTIVE_INSTANCES) {
                ACTIVE_INSTANCES.remove(registeredUsername, this);
            }
        }
    }
}
