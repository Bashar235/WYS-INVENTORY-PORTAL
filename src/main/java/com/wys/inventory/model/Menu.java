package com.wys.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "menus")
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_code", nullable = false, unique = true, length = 100)
    private String menuCode;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "route_url", nullable = false, length = 200)
    private String routeUrl;

    @Column(length = 100)
    private String icon;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    public Menu() {}

    public Long    getId()           { return id; }
    public String  getMenuCode()     { return menuCode; }
    public void    setMenuCode(String menuCode)      { this.menuCode = menuCode; }
    public String  getLabel()        { return label; }
    public void    setLabel(String label)            { this.label = label; }
    public String  getRouteUrl()     { return routeUrl; }
    public void    setRouteUrl(String routeUrl)      { this.routeUrl = routeUrl; }
    public String  getIcon()         { return icon; }
    public void    setIcon(String icon)              { this.icon = icon; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void    setDisplayOrder(Integer d)        { this.displayOrder = d; }

    @Override public String toString() { return label; }

    /**
     * Identity is based on the database id, never on object identity.
     * Role.menus is a HashSet: without this, adding a Menu instance loaded from a
     * different persistence context would be treated as a new element and would
     * insert a duplicate role_menus row, violating the (role_id, menu_id) primary key.
     */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && id.equals(((Menu) o).id);
    }

    @Override public int hashCode() { return getClass().hashCode(); }
}
