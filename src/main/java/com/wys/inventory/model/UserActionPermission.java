package com.wys.inventory.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the user_actions override table.
 *
 * See {@link UserMenuPermission} for why the join table is mapped as an
 * entity: the {@code granted} column (TRUE = grant, FALSE = explicit deny)
 * cannot be represented by a {@code @ManyToMany} association.
 */
@Entity
@Table(name = "user_actions")
@IdClass(UserActionPermissionId.class)
public class UserActionPermission {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id", insertable = false, updatable = false)
    private Action action;

    @Column(nullable = false)
    private boolean granted = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserActionPermission() {}

    public UserActionPermission(Long userId, Long actionId, boolean granted) {
        this.userId    = userId;
        this.actionId  = actionId;
        this.granted   = granted;
    }

    public Long    getUserId()             { return userId; }
    public void    setUserId(Long id)      { this.userId = id; }
    public Long    getActionId()           { return actionId; }
    public void    setActionId(Long id)    { this.actionId = id; }

    public User    getUser()               { return user; }
    public void    setUser(User user)      { this.user = user; }
    public Action  getAction()             { return action; }
    public void    setAction(Action action){ this.action = action; }

    public boolean isGranted()             { return granted; }
    public void    setGranted(boolean granted) { this.granted = granted; }

    public Instant getCreatedAt()          { return createdAt; }
    public void    setCreatedAt(Instant t) { this.createdAt = t; }
}
