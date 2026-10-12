package com.wys.inventory;

import com.wys.inventory.model.Product;
import com.wys.inventory.repository.AuditLogRepository;
import com.wys.inventory.repository.ProductRepository;
import com.wys.inventory.service.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private AuditLogRepository auditLogRepository;
    private ApplicationEventPublisher eventPublisher;
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        productService = new ProductServiceImpl(productRepository, auditLogRepository, eventPublisher);
    }

    @Test
    void testUpdateProductSavesDirectlyAndLogsAudit() {
        Product p = new Product("TEST-1", "Test Product", "Electronics", 10, 5, BigDecimal.valueOf(29.99));
        p.setId(123L);

        when(productRepository.save(any(Product.class))).thenReturn(p);

        Product updated = productService.update(p);

        assertNotNull(updated);
        assertEquals("TEST-1", updated.getSku());
        verify(productRepository, times(1)).save(p);
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    void testProductAttributesSummaryFormatting() {
        Product p = new Product("TEST-2", "Gadget", "Electronics", 5, 2, BigDecimal.valueOf(9.99));
        // No attributes → should return em dash
        assertEquals("—", p.getAttributesSummary());

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("warranty", "24m");
        p.setAttributes(attrs);

        String summary = p.getAttributesSummary();
        assertTrue(summary.contains("Warranty"));
        assertTrue(summary.contains("24m"));
    }
}
