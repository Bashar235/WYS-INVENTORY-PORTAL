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
import java.util.List;

@Service("productService")
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductServiceImpl(ProductRepository productRepository,
                              AuditLogRepository auditLogRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    public List<Product> findByCategory(String category) {
        if (category == null || category.trim().isEmpty() || "ALL".equalsIgnoreCase(category)) {
            return findAll();
        }
        return productRepository.findByCategoryIgnoreCase(category);
    }

    @Override
    public List<Product> findLowStock() {
        return productRepository.findLowStockProducts();
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    @Override
    @Transactional
    public Product create(Product product) {
        if (productRepository.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("Product with SKU '" + product.getSku() + "' already exists.");
        }
        Product saved = productRepository.save(product);

        AuditLog log = new AuditLog(
                saved.getId(), saved.getSku(), "CREATED",
                saved.getQuantity(), 0, saved.getQuantity(),
                "Initial catalog entry", "SYSTEM"
        );
        auditLogRepository.save(log);
        eventPublisher.publishEvent(new StockChangedEvent("CREATED", saved.getSku()));
        return saved;
    }

    @Override
    @Transactional
    public Product update(Product product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("Cannot update product without an ID.");
        }
        product.setLastUpdated(Instant.now());
        Product saved = productRepository.save(product);

        AuditLog log = new AuditLog(
                saved.getId(), saved.getSku(), "UPDATED",
                0, saved.getQuantity(), saved.getQuantity(),
                "Catalog inline edit saved", "SYSTEM"
        );
        auditLogRepository.save(log);
        eventPublisher.publishEvent(new StockChangedEvent("UPDATED", saved.getSku()));
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id != null) {
            productRepository.deleteById(id);
            eventPublisher.publishEvent(new StockChangedEvent("DELETED", null));
        }
    }
}
