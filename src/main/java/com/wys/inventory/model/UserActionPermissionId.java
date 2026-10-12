package com.wys.inventory.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for the user_actions override table.
 * Mirrors the (user_id, action_id) primary key defined in V4__user_permissions.sql.
 */
public class UserActionPermissionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long actionId;

    public UserActionPermissionId() {}

    public UserActionPermissionId(Long userId, Long actionId) {
        this.userId   = userId;
        this.actionId = actionId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getActionId() { return actionId; }
    public void setActionId(Long actionId) { this.actionId = actionId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserActionPermissionId that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(actionId, that.actionId);
    }

    @Override public int hashCode() {
        return Objects.hash(userId, actionId);
    }
}
