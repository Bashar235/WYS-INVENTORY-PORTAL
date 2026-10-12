package com.wys.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "actions")
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_code", nullable = false, unique = true, length = 100)
    private String actionCode;

    @Column(name = "action_name", nullable = false, length = 150)
    private String actionName;

    @Column(length = 255)
    private String description;

    public Action() {}

    public Long   getId()          { return id; }
    public String getActionCode()  { return actionCode; }
    public void   setActionCode(String actionCode)   { this.actionCode = actionCode; }
    public String getActionName()  { return actionName; }
    public void   setActionName(String actionName)   { this.actionName = actionName; }
    public String getDescription() { return description; }
    public void   setDescription(String d)           { this.description = d; }

    @Override public String toString() { return actionCode; }

    /**
     * Identity is based on the database id, never on object identity.
     * Role.actions is a HashSet: without this, adding an Action instance loaded from a
     * different persistence context would be treated as a new element and would insert a
     * duplicate role_actions row, violating the (role_id, action_id) primary key.
     */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && id.equals(((Action) o).id);
    }

    @Override public int hashCode() { return getClass().hashCode(); }
}
