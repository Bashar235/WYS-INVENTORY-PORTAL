package com.wys.inventory.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 50)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_menus",
        joinColumns        = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "menu_id"))
    private Set<Menu> menus = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_actions",
        joinColumns        = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "action_id"))
    private Set<Action> actions = new HashSet<>();

    public Role() {}

    public Role(String roleCode, String roleName, String description) {
        this.roleCode    = roleCode;
        this.roleName    = roleName;
        this.description = description;
    }

    public Long   getId()          { return id; }
    public String getRoleCode()    { return roleCode; }
    public void   setRoleCode(String roleCode)  { this.roleCode = roleCode; }
    public String getRoleName()    { return roleName; }
    public void   setRoleName(String roleName)  { this.roleName = roleName; }
    public String getDescription() { return description; }
    public void   setDescription(String d)      { this.description = d; }
    public Set<Menu>   getMenus()   { return menus; }
    public void setMenus(Set<Menu> menus)       { this.menus = menus; }
    public Set<Action> getActions() { return actions; }
    public void setActions(Set<Action> actions) { this.actions = actions; }

    @Override public String toString() { return roleCode; }

    /** True when this role owns the given menu (id based). */
    public boolean hasMenu(Long menuId) {
        if (menuId == null) return false;
        return menus.stream().anyMatch(m -> menuId.equals(m.getId()));
    }

    /** True when this role owns the given action (id based). */
    public boolean hasAction(Long actionId) {
        if (actionId == null) return false;
        return actions.stream().anyMatch(a -> actionId.equals(a.getId()));
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && id.equals(((Role) o).id);
    }

    @Override public int hashCode() { return getClass().hashCode(); }
}
