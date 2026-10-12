package com.wys.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the users table.
 * Each user has exactly one role (stored as a FK to roles.id).
 * Users can also have individual permission overrides via user_menus and user_actions.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(length = 150, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    /**
     * Per-user permission overrides live in the user_menus / user_actions
     * join tables and are mapped by {@link UserMenuPermission} /
     * {@link UserActionPermission}. They are read through
     * UserMenuPermissionRepository / UserActionPermissionRepository so that
     * the {@code granted} column (TRUE = grant, FALSE = explicit deny)
     * can be honoured — a plain {@code @ManyToMany} cannot represent it.
     */

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User() {}

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role     = role;
    }

    public Long    getId()        { return id; }
    public void    setId(Long id) { this.id = id; }

    public String  getUsername()               { return username; }
    public void    setUsername(String username) { this.username = username; }

    public String  getPassword()               { return password; }
    public void    setPassword(String password) { this.password = password; }

    public String  getEmail()              { return email; }
    public void    setEmail(String email)  { this.email = email; }

    public boolean isEnabled()               { return enabled; }
    public void    setEnabled(boolean b)     { this.enabled = b; }

    public Role    getRole()               { return role; }
    public void    setRole(Role role)      { this.role = role; }

    /** Convenience getter that returns role_code string (e.g. "ADMIN") or null */
    public String  getRoleCode()  { return role != null ? role.getRoleCode() : null; }

    public Instant getCreatedAt()              { return createdAt; }
    public void    setCreatedAt(Instant t)     { this.createdAt = t; }

    @Override public String toString() { return username; }
}
