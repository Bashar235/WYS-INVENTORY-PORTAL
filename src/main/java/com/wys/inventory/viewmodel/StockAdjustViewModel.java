package com.wys.inventory.viewmodel;

import com.wys.inventory.model.Product;
import com.wys.inventory.service.AccessControlService;
import com.wys.inventory.service.ProductService;
import com.wys.inventory.service.StockService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ViewModel for stockAdjust.zul.
 * Stock submission is guarded by STOCK_ADJUST_SUBMIT action permission.
 */
@VariableResolver(DelegatingVariableResolver.class)
public class StockAdjustViewModel {

    @WireVariable
    private ProductService productService;

    @WireVariable
    private StockService stockService;

    @WireVariable
    private AccessControlService accessControlService;

    private List<Product> allProducts      = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private Product selectedProduct;
    private String searchText = "";
    private int    delta = 0;
    private int    previewQty = 0;
    private String adjustmentType;
    private String note = "";

    @Init
    public void init() {
        loadProducts();
    }

    private void loadProducts() {
        List<Product> fetched = productService.findByCategory("ALL");
        allProducts = fetched != null ? fetched : new ArrayList<>();
        filterProducts();
    }

    @Command
    @NotifyChange({"filteredProducts", "totalProducts", "searchText"})
    public void search(@BindingParam("value") String value) {
        if (value != null) this.searchText = value;
        filterProducts();
    }

    @Command
    @NotifyChange({"filteredProducts", "totalProducts", "searchText"})
    public void clearSearch() {
        this.searchText = "";
        filterProducts();
    }

    private void filterProducts() {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredProducts = new ArrayList<>(allProducts);
        } else {
            String lower = searchText.toLowerCase();
            filteredProducts = allProducts.stream()
                    .filter(p -> (p.getSku()  != null && p.getSku().toLowerCase().contains(lower))
                              || (p.getName() != null && p.getName().toLowerCase().contains(lower)))
                    .collect(Collectors.toList());
        }
    }

    @Command
    @NotifyChange({"selectedProduct", "delta", "previewQty", "adjustmentType", "note"})
    public void selectProduct(@BindingParam("product") Product product) {
        this.selectedProduct = product;
        resetFormInternal();
    }

    @Command
    @NotifyChange({"delta", "previewQty"})
    public void quickDelta(@BindingParam("amount") int amount) {
        if (selectedProduct != null) {
            this.delta += amount;
            updatePreviewInternal();
        }
    }

    @Command
    @NotifyChange("previewQty")
    public void updatePreview() { updatePreviewInternal(); }

    private void updatePreviewInternal() {
        this.previewQty = (selectedProduct != null) ? selectedProduct.getQuantity() + this.delta : 0;
    }

    @Command
    @NotifyChange({"delta", "previewQty", "adjustmentType", "note"})
    public void resetForm() { resetFormInternal(); }

    private void resetFormInternal() {
        this.delta          = 0;
        this.adjustmentType = null;
        this.note           = "";
        updatePreviewInternal();
    }

    @Command
    @NotifyChange({"filteredProducts", "selectedProduct", "delta", "previewQty", "adjustmentType", "note", "totalProducts"})
    public void save() {
        if (!accessControlService.canDo("STOCK_ADJUST_SUBMIT")) {
            Clients.showNotification("You do not have permission to adjust stock.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        if (selectedProduct == null) {
            Clients.showNotification("Please select a product first.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        if (delta == 0) {
            Clients.showNotification("Custom change (+/-) cannot be zero.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        if (previewQty < 0) {
            Clients.showNotification("Stock cannot drop below zero.",
                    Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3000);
            return;
        }

        // Resolve the logged-in username for audit attribution
        String performedBy = "System";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) performedBy = auth.getName();

        try {
            String fullNote = (adjustmentType != null ? "[" + adjustmentType + "] " : "") + note;
            Product updated = stockService.adjustStock(selectedProduct.getId(), delta, fullNote, performedBy);
            Clients.showNotification("Stock updated for " + updated.getSku() + "!",
                    Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
            loadProducts();
            final Long updatedId = updated.getId();
            this.selectedProduct = allProducts.stream()
                    .filter(p -> p.getId().equals(updatedId))
                    .findFirst().orElse(null);
            resetFormInternal();
        } catch (Exception e) {
            Clients.showNotification("Error updating stock: " + e.getMessage(),
                    Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3500);
        }
    }

    // ---- Getters & Setters ----
    public Integer getTotalProducts()   { return filteredProducts != null ? filteredProducts.size() : 0; }
    public List<Product> getFilteredProducts() { return filteredProducts; }
    public Product getSelectedProduct()        { return selectedProduct; }
    public void    setSelectedProduct(Product p) { this.selectedProduct = p; }
    public String  getSearchText()             { return searchText; }
    public void    setSearchText(String t)     { this.searchText = t; }
    public int     getDelta()                  { return delta; }
    public void    setDelta(int delta)         { this.delta = delta; }
    public int     getPreviewQty()             { return previewQty; }
    public String  getAdjustmentType()         { return adjustmentType; }
    public void    setAdjustmentType(String t) { this.adjustmentType = t; }
    public String  getNote()                   { return note; }
    public void    setNote(String note)        { this.note = note; }
    public boolean canAdjustStock()            { return accessControlService.canDo("STOCK_ADJUST_SUBMIT"); }
}
