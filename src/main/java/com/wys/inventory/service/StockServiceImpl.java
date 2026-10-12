package com.wys.inventory.service;

import com.wys.inventory.event.StockChangedEvent;
import com.wys.inventory.model.AuditLog;
import com.wys.inventory.model.Product;
import com.wys.inventory.repository.AuditLogRepository;
import com.wys.inventory.repository.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service("stockService")
public class StockServiceImpl implements StockService {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StockServiceImpl(ProductRepository productRepository,
                            AuditLogRepository auditLogRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.productRepository  = productRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher     = eventPublisher;
    }

    @Override
    @Transactional
    public Product adjustStock(Long productId, int delta, String note, String user) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        int previousQty = product.getQuantity();
        int newQty      = previousQty + delta;

        if (newQty < 0) {
            throw new IllegalArgumentException(
                "Stock quantity cannot be negative. Current: " + previousQty + ", Delta: " + delta);
        }

        product.setQuantity(newQty);
        product.setLastUpdated(Instant.now());
        Product updatedProduct = productRepository.save(product);

        AuditLog auditLog = new AuditLog(
                productId,
                product.getSku(),
                "STOCK_ADJUSTMENT",
                delta,
                previousQty,
                newQty,
                note,
                user != null ? user : "Warehouse Manager"
        );
        auditLogRepository.save(auditLog);
        eventPublisher.publishEvent(new StockChangedEvent("STOCK_ADJUSTMENT", product.getSku()));
        return updatedProduct;
    }
}
