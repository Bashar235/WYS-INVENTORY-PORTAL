package com.wys.inventory.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the user_menus override table.
 *
 * The table was created by V4__user_permissions.sql with a {@code granted}
 * column: TRUE = explicit grant, FALSE = explicit revoke (deny) that
 * overrides the role's inherited permission. The previous
 * {@code @ManyToMany} mapping on {@link User} could not represent that
 * column — it could only store (user_id, menu_id) pairs, so a deny was
 * impossible. Mapping the join table as an entity makes the {@code granted}
 * flag usable and lets a Super Admin revoke a role-inherited permission from
 * a single user.
 */
@Entity
@Table(name = "user_menus")
@IdClass(UserMenuPermissionId.class)
public class UserMenuPermission {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", insertable = false, updatable = false)
    private Menu menu;

    @Column(nullable = false)
    private boolean granted = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserMenuPermission() {}

    public UserMenuPermission(Long userId, Long menuId, boolean granted) {
        this.userId   = userId;
        this.menuId   = menuId;
        this.granted  = granted;
    }

    public Long    getUserId()          { return userId; }
    public void    setUserId(Long id)   { this.userId = id; }
    public Long    getMenuId()          { return menuId; }
    public void    setMenuId(Long id)   { this.menuId = id; }

    public User    getUser()            { return user; }
    public void    setUser(User user)   { this.user = user; }
    public Menu    getMenu()            { return menu; }
    public void    setMenu(Menu menu)   { this.menu = menu; }

    public boolean isGranted()          { return granted; }
    public void    setGranted(boolean granted) { this.granted = granted; }

    public Instant getCreatedAt()       { return createdAt; }
    public void    setCreatedAt(Instant t) { this.createdAt = t; }
}
