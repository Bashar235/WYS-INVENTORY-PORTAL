package com.wys.inventory.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Product category. Stores suggested dynamic attribute keys as a comma-separated
 * TEXT column (converted to List<String> in Java).
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Stored as JSON-like text in the DB (e.g. '["warrantyMonths","serialNumber"]').
     * Converted via @Convert using a simple comma-string approach.
     */
    @Column(name = "suggested_attribute_keys", columnDefinition = "TEXT")
    @Convert(converter = com.wys.inventory.util.StringListConverter.class)
    private List<String> suggestedAttributeKeys = new ArrayList<>();

    public Category() {}

    public Category(String name) {
        this.name = name;
    }

    public Category(String name, List<String> suggestedAttributeKeys) {
        this.name = name;
        this.suggestedAttributeKeys = suggestedAttributeKeys != null ? suggestedAttributeKeys : new ArrayList<>();
    }

    public Long   getId()   { return id; }
    public void   setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void   setName(String name) { this.name = name; }

    public List<String> getSuggestedAttributeKeys() { return suggestedAttributeKeys; }
    public void setSuggestedAttributeKeys(List<String> keys) {
        this.suggestedAttributeKeys = keys != null ? keys : new ArrayList<>();
    }

    @Override
    public String toString() { return name; }
}
