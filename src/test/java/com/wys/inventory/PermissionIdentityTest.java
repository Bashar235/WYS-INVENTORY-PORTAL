package com.wys.inventory;

import com.wys.inventory.model.Action;
import com.wys.inventory.model.Menu;
import com.wys.inventory.model.Role;
import com.wys.inventory.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the permission persistence fix.
 *
 * The permission page could never save because {@code Menu} and {@code Action} used
 * identity-based {@code equals}/{@code hashCode}. Granting a permission loaded from a
 * different persistence context produced a duplicate {@code HashSet} element, which
 * Hibernate flushed as a second join-table row and rejected against the
 * {@code (role_id, menu_id)} / {@code (role_id, action_id)} primary key.
 *
 * These tests pin the id-based identity semantics that make the grant/revoke path safe.
 * Ids are injected reflectively because they are database generated and intentionally
 * have no setters.
 */
class PermissionIdentityTest {

    private Role role;
    private Menu menu;
    private Action action;

    @BeforeEach
    void setUp() {
        role = new Role("SUPER_ADMIN", "Super Administrator", "Full access");
        setId(role, 1L);

        menu = new Menu();
        setId(menu, 7L);
        menu.setMenuCode("MENU_PERM_ADMIN");
        menu.setLabel("Permission Management");
        menu.setRouteUrl("~./zul/permissionAdmin.zul");

        action = new Action();
        setId(action, 9L);
        action.setActionCode("PERM_ADMIN_ACCESS");
        action.setActionName("Access Permission Management");
    }

    private static void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    /** Two distinct Java objects representing the same database row. */
    private Menu duplicateOf(Menu source) {
        Menu copy = new Menu();
        setId(copy, source.getId());
        copy.setMenuCode(source.getMenuCode());
        copy.setLabel(source.getLabel());
        copy.setRouteUrl(source.getRouteUrl());
        return copy;
    }

    private Action duplicateOf(Action source) {
        Action copy = new Action();
        setId(copy, source.getId());
        copy.setActionCode(source.getActionCode());
        copy.setActionName(source.getActionName());
        return copy;
    }

    @Test
    @DisplayName("Menu instances with the same id collapse to one Set element")
    void duplicateMenuDoesNotDuplicateSetElement() {
        Set<Menu> granted = new HashSet<>();
        granted.add(menu);

        boolean added = granted.add(duplicateOf(menu));

        assertFalse(added, "A second instance of the same menu row must not be added");
        assertEquals(1, granted.size());
    }

    @Test
    @DisplayName("Action instances with the same id collapse to one Set element")
    void duplicateActionDoesNotDuplicateSetElement() {
        Set<Action> granted = new HashSet<>();
        granted.add(action);

        boolean added = granted.add(duplicateOf(action));

        assertFalse(added, "A second instance of the same action row must not be added");
        assertEquals(1, granted.size());
    }

    @Test
    @DisplayName("Distinct rows remain distinct and removable")
    void distinctRowsStayIndependent() {
        Menu other = new Menu();
        setId(other, 8L);
        other.setMenuCode("MENU_CATALOG");

        assertNotEquals(menu, other);

        Set<Menu> granted = new HashSet<>();
        granted.add(menu);
        granted.add(other);
        assertEquals(2, granted.size());

        assertTrue(granted.remove(duplicateOf(other)));
        assertEquals(1, granted.size());
        assertTrue(granted.contains(menu));
    }

    @Test
    @DisplayName("Transient rows are never equal, so they never overwrite persisted ones")
    void transientRowsAreNotEqual() {
        Menu transientMenu = new Menu();
        transientMenu.setMenuCode("MENU_DASHBOARD");

        assertNotEquals(menu, transientMenu);
    }

    @Test
    @DisplayName("Role.hasMenu / hasAction match on id, not instance")
    void roleLookupsMatchOnId() {
        role.getMenus().add(menu);
        role.getActions().add(action);

        assertTrue(role.hasMenu(7L));
        assertTrue(role.hasMenu(menu.getId()));
        assertFalse(role.hasMenu(99L));

        assertTrue(role.hasAction(9L));
        assertFalse(role.hasAction(99L));
    }

    @Test
    @DisplayName("RolePermissions snapshot exposes granted codes and counts")
    void rolePermissionsSnapshotSummarisesGrants() {
        Set<String> menus = new LinkedHashSet<>(Set.of("MENU_DASHBOARD", "MENU_PERM_ADMIN"));
        Set<String> actions = new LinkedHashSet<>(Set.of("PERM_ADMIN_ACCESS"));

        PermissionService.RolePermissions snapshot =
                new PermissionService.RolePermissions(1L, "SUPER_ADMIN", "Super Administrator",
                        "Full access", menus, actions);

        assertEquals(2, snapshot.getGrantedMenuCount());
        assertEquals(1, snapshot.getGrantedActionCount());
        assertEquals(3, snapshot.getGrantedTotal());
        assertTrue(snapshot.has("MENU_PERM_ADMIN"));
        assertTrue(snapshot.has("PERM_ADMIN_ACCESS"));
        assertFalse(snapshot.has("ITEM_CREATE_SUBMIT"));
    }

    @Test
    @DisplayName("PermissionItem renders a non-null hint for menus without a description")
    void permissionItemAlwaysHasAHint() {
        PermissionService.PermissionItem menuItem = PermissionService.PermissionItem.ofMenu(menu);

        assertTrue(menuItem.isMenu());
        assertEquals("MENU_PERM_ADMIN", menuItem.getCode());
        assertEquals("Permission Management", menuItem.getLabel());
        assertNotNull(menuItem.getHint());
        assertTrue(menuItem.getHint().contains("permissionAdmin.zul"));
        assertEquals("perm-row-icon perm-row-icon-menu", menuItem.getGlyphClass());
    }

    @Test
    @DisplayName("PermissionItem renders the seeded action description")
    void actionItemUsesItsDescription() {
        action.setDescription("Allows viewing and editing role permissions");
        PermissionService.PermissionItem actionItem = PermissionService.PermissionItem.ofAction(action);

        assertTrue(actionItem.isAction());
        assertEquals("PERM_ADMIN_ACCESS", actionItem.getCode());
        assertEquals("Allows viewing and editing role permissions", actionItem.getHint());
        assertEquals("perm-row-icon perm-row-icon-action", actionItem.getGlyphClass());
    }

    @Test
    @DisplayName("Menu and action codes live in separate namespaces")
    void menuAndActionCodesDoNotCollide() {
        assertNotEquals(menu.getMenuCode(), action.getActionCode());
        assertTrue(menu.getMenuCode().startsWith("MENU_"));
        assertFalse(action.getActionCode().startsWith("MENU_"));
    }
}
