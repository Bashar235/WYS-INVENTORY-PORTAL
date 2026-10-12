package com.wys.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the audit_logs table.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(length = 100)
    private String sku;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "change_amount", nullable = false)
    private int changeAmount;

    @Column(name = "previous_quantity", nullable = false)
    private int previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private int newQuantity;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public AuditLog() {}

    public AuditLog(Long productId, String sku, String action,
                    int changeAmount, int previousQuantity, int newQuantity,
                    String note, String performedBy) {
        this.productId        = productId;
        this.sku              = sku;
        this.action           = action;
        this.changeAmount     = changeAmount;
        this.previousQuantity = previousQuantity;
        this.newQuantity      = newQuantity;
        this.note             = note;
        this.performedBy      = performedBy;
    }
     public AuditLog(String productId, String sku, String action,
                    int changeAmount, int previousQuantity, int newQuantity,
                    String note, String performedBy) {
        if (productId != null) {
            try {
                this.productId = Long.parseLong(productId);
            } catch (NumberFormatException ignored) {}
        }
        this.sku              = sku;
        this.action           = action;
        this.changeAmount     = changeAmount;
        this.previousQuantity = previousQuantity;
        this.newQuantity      = newQuantity;
        this.note             = note;
        this.performedBy      = performedBy;
    }

    public Long    getId()               { return id; }
    public void    setId(Long id)        { this.id = id; }

    public Long    getProductId()            { return productId; }
    public void    setProductId(Long p)      { this.productId = p; }

    public String  getSku()              { return sku; }
    public void    setSku(String sku)    { this.sku = sku; }

    public String  getAction()               { return action; }
    public void    setAction(String action)  { this.action = action; }

    public int     getChangeAmount()                  { return changeAmount; }
    public void    setChangeAmount(int changeAmount)  { this.changeAmount = changeAmount; }

    public int     getPreviousQuantity()                      { return previousQuantity; }
    public void    setPreviousQuantity(int previousQuantity)  { this.previousQuantity = previousQuantity; }

    public int     getNewQuantity()                   { return newQuantity; }
    public void    setNewQuantity(int newQuantity)    { this.newQuantity = newQuantity; }

    public String  getNote()             { return note; }
    public void    setNote(String note)  { this.note = note; }

    public String  getPerformedBy()                  { return performedBy; }
    public void    setPerformedBy(String performedBy){ this.performedBy = performedBy; }

    public Instant getTimestamp()              { return timestamp; }
    public void    setTimestamp(Instant t)     { this.timestamp = t; }
}
