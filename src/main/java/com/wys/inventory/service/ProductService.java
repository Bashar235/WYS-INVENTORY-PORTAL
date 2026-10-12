package com.wys.inventory.service;

import com.wys.inventory.model.Product;
import java.util.List;

public interface ProductService {
    List<Product> findAll();
    List<Product> findByCategory(String category);
    List<Product> findLowStock();
    Product findById(Long id);
    Product create(Product product);
    Product update(Product product);
    void delete(Long id);
}
