package com.wys.inventory.service;

import com.wys.inventory.model.AuditLog;
import com.wys.inventory.model.Product;

import java.util.List;
import java.util.Map;

public interface InventoryReportService {
    List<Product> getLowStockReport();
    List<AuditLog> getAuditHistoryForProduct(Long productId);
    Map<String, Object> getInventorySummaryMetrics();
}