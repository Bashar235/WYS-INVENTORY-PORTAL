package com.wys.inventory.repository;

import com.wys.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryIgnoreCase(String category);

    /** Products where current quantity is below the reorder level. */
    @Query("SELECT p FROM Product p WHERE p.quantity < p.reorderLevel")
    List<Product> findLowStockProducts();

    boolean existsBySku(String sku);
}
