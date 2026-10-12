package com.wys.inventory.viewmodel;

import com.wys.inventory.model.User;

/** A user entry shown under the selected role in the member panel. */
public final class UserItem {
    private final User user;
    private final Long roleId;

    public UserItem(User user, Long roleId) {
        this.user = user;
        this.roleId = roleId;
    }

    public User getUser() { return user; }
    public Long getRoleId() { return roleId; }

    /** Returns the first character of the username in uppercase for the avatar. */
    public String getAvatarInitial() {
        String username = user != null ? user.getUsername() : "";
        if (username.isEmpty()) return "?";
        return username.substring(0, 1).toUpperCase();
    }
}
