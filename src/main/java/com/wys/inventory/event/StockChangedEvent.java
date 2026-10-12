package com.wys.inventory.event;

/**
 * Published (via Spring's ApplicationEventPublisher) whenever product data
 * or stock levels change in the database.
 *
 * @param type what happened: CREATED, UPDATED, DELETED or STOCK_ADJUSTMENT
 * @param sku  SKU of the affected product, if known (may be null)
 */
public record StockChangedEvent(String type, String sku) { }