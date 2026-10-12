package com.wys.inventory.service;

import com.wys.inventory.model.AuditLog;
import com.wys.inventory.model.Product;
import com.wys.inventory.repository.AuditLogRepository;
import com.wys.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryReportServiceImpl implements InventoryReportService {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;

    public InventoryReportServiceImpl(ProductRepository productRepository, AuditLogRepository auditLogRepository) {
        this.productRepository = productRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public List<Product> getLowStockReport() {
        return productRepository.findLowStockProducts();
    }

    @Override
    public List<AuditLog> getAuditHistoryForProduct(Long productId) {
        return auditLogRepository.findByProductIdOrderByTimestampDesc(productId);
    }

    public List<AuditLog> getAuditHistoryForProduct(String productId) {
        try {
            return getAuditHistoryForProduct(productId != null ? Long.parseLong(productId) : null);
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getInventorySummaryMetrics() {
        List<Product> allProducts = productRepository.findAll();

        long totalItemsCount = allProducts.size();
        long totalStockUnits = allProducts.stream().mapToLong(Product::getQuantity).sum();
        BigDecimal totalValue = allProducts.stream()
    .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
        long lowStockCount = allProducts.stream().filter(p -> p.getQuantity() < p.getReorderLevel()).count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalProducts", totalItemsCount);
        metrics.put("totalStockUnits", totalStockUnits);
        metrics.put("totalInventoryValue", totalValue);
        metrics.put("lowStockAlerts", lowStockCount);

        return metrics;
    }
}