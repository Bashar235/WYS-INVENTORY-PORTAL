package com.wys.inventory.model;

import com.wys.inventory.util.JsonMapConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity for the products table.
 * The dynamic attributes map is serialized as JSONB via JsonMapConverter.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "JSONB")
    private Map<String, Object> attributes = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    public Product() {}

    public Product(String sku, String name, String category, int quantity, int reorderLevel, BigDecimal unitPrice) {
        this.sku          = sku;
        this.name         = name;
        this.category     = category;
        this.quantity     = quantity;
        this.reorderLevel = reorderLevel;
        this.unitPrice    = unitPrice;
    }

    // Getters and Setters
    public Long   getId()   { return id; }
    public void   setId(Long id) { this.id = id; }

    public String getSku()  { return sku; }
    public void   setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void   setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void   setCategory(String category) { this.category = category; }

    public int    getQuantity() { return quantity; }
    public void   setQuantity(int quantity) { this.quantity = quantity; }

    public int    getReorderLevel() { return reorderLevel; }
    public void   setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void   setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    public Instant getCreatedAt()  { return createdAt; }
    public void    setCreatedAt(Instant t) { this.createdAt = t; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void    setLastUpdated(Instant t) { this.lastUpdated = t; }

    /** Returns a short, user-friendly summary of dynamic attributes. */
    public String getAttributesSummary() {
        if (attributes == null || attributes.isEmpty()) {
            return "\u2014";
        }
        Map.Entry<String, Object> first = attributes.entrySet().iterator().next();
        String firstEntry = capitalize(first.getKey()) + ": " + first.getValue();
        if (attributes.size() == 1) {
            return firstEntry;
        }
        return firstEntry + "  (+" + (attributes.size() - 1) + " more)";
    }

    /** Returns all attributes as a pipe-separated list for tooltip display. */
    public String getFullAttributesSummary() {
        if (attributes == null || attributes.isEmpty()) {
            return "No custom attributes";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(capitalize(entry.getKey())).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
