package com.wys.inventory.service;

import com.wys.inventory.model.Product;

public interface StockService {
    Product adjustStock(Long productId, int delta, String note, String user);
}
