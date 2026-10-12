package com.wys.inventory.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for the user_menus override table.
 * Mirrors the (user_id, menu_id) primary key defined in V4__user_permissions.sql.
 */
public class UserMenuPermissionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long menuId;

    public UserMenuPermissionId() {}

    public UserMenuPermissionId(Long userId, Long menuId) {
        this.userId  = userId;
        this.menuId  = menuId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserMenuPermissionId that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(menuId, that.menuId);
    }

    @Override public int hashCode() {
        return Objects.hash(userId, menuId);
    }
}
