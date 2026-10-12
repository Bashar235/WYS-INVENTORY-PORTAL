package com.wys.inventory.viewmodel;

import com.wys.inventory.model.Product;
import com.wys.inventory.service.AccessControlService;
import com.wys.inventory.service.ProductService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Path;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zul.Include;
import org.zkoss.zul.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for catalog.zul and itemCreator.zul.
 * All permission checks are delegated to AccessControlService (dynamic, DB-driven).
 */
@VariableResolver(DelegatingVariableResolver.class)
public class ProductViewModel {

    @WireVariable
    private ProductService productService;

    @WireVariable
    private com.wys.inventory.service.CategoryService categoryService;

    @WireVariable
    private AccessControlService accessControlService;

    private List<Product> products;
    private List<com.wys.inventory.model.Category> categories;
    private List<String> filterCategories;
    private String selectedCategory = "ALL";
    private boolean lowStockOnly    = false;
    private Product selectedProduct;
    private Product editingProduct;
    private Product newProduct = new Product();
    private List<AttributeRow> dynamicAttributes = new ArrayList<>();

    @Init
    public void init() {
        loadCategories();
        loadProducts();
    }

    // ---- Dynamic permission helpers ----

    public boolean canEdit()         { return accessControlService.canDo("CATALOG_DOUBLECLICK_EDIT"); }
    public boolean canDelete()       { return accessControlService.canDo("CATALOG_SINGLECLICK_DELETE"); }
    public boolean canSave()         { return accessControlService.canDo("CATALOG_SAVE_PRODUCT"); }
    public boolean canCreateItem()   { return accessControlService.canDo("ITEM_CREATE_SUBMIT"); }
    public boolean canAddAttribute() { return accessControlService.canDo("ITEM_ADD_ATTRIBUTE"); }
    public boolean canEditAttributes() { return accessControlService.canDo("CATALOG_OPEN_ATTR_EDITOR"); }

    // ---- Bean-style permission properties consumed by catalog.zul ----
    // ZK's expression language resolves properties through the JavaBeans
    // Introspector, so only getXxx()/isXxx() accessors are reachable from
    // ${vm.*}. The bare can*() methods above are used internally by the command
    // implementations but are invisible to EL, which is why the view must bind
    // to the is* accessors below instead.

    /** MENU / ITEM_CREATE_SUBMIT - allows opening the new item creator. */
    public boolean isCanCreateItems()      { return canCreateItem(); }

    /** CATALOG_DOUBLECLICK_EDIT - allows starting an inline row edit. */
    public boolean isCanEditProducts()     { return canEdit(); }

    /** CATALOG_SAVE_PRODUCT - allows persisting inline row edits. */
    public boolean isCanSaveProducts()     { return canSave(); }

    /** CATALOG_SINGLECLICK_DELETE - allows deleting the selected product. */
    public boolean isCanDeleteProducts()   { return canDelete(); }

    /** CATALOG_OPEN_ATTR_EDITOR - allows opening the dynamic attribute editor. */
    public boolean isCanEditAttributes()   { return canEditAttributes(); }

    /** ITEM_ADD_ATTRIBUTE - allows adding/removing dynamic attribute rows. */
    public boolean isCanAddAttributes()    { return canAddAttribute(); }

    /** True when an inline edit session is currently open. */
    public boolean isEditing()             { return editingProduct != null; }

    // ---- Commands ----

    @Command
    @NotifyChange({"categories", "filterCategories"})
    public void loadCategories() {
        this.categories = categoryService.findAll();
        this.filterCategories = new ArrayList<>();
        this.filterCategories.add("ALL");
        for (com.wys.inventory.model.Category c : this.categories) {
            this.filterCategories.add(c.getName());
        }
    }

    @Command
    @NotifyChange("products")
    public void loadProducts() {
        if (lowStockOnly) {
            products = productService.findLowStock();
        } else {
            products = productService.findByCategory(selectedCategory);
        }
    }

    @Command
    @NotifyChange({"products", "lowStockOnly"})
    public void toggleLowStock() {
        this.lowStockOnly = !this.lowStockOnly;
        loadProducts();
    }

    @Command
    public void openItemCreator(@ContextParam(ContextType.VIEW) Component view) {
        if (!canCreateItem()) {
            Clients.showNotification("You do not have permission to add products.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3000);
            return;
        }
        switchMainContent(view, "~./zul/itemCreator.zul");
    }

    @Command
    @NotifyChange({"newProduct", "dynamicAttributes"})
    public void backToCatalog(@ContextParam(ContextType.VIEW) Component view) {
        this.newProduct = new Product();
        this.dynamicAttributes = new ArrayList<>();
        switchMainContent(view, "~./zul/catalog.zul");
    }

    @Command
    @NotifyChange("dynamicAttributes")
    public void addAttribute() {
        if (!canAddAttribute()) {
            Clients.showNotification("You do not have permission to add attributes.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3000);
            return;
        }
        dynamicAttributes.add(new AttributeRow());
    }

    @Command
    @NotifyChange("dynamicAttributes")
    public void removeAttribute(@BindingParam("attr") AttributeRow attr) {
        if (!canAddAttribute()) {
            Clients.showNotification("You do not have permission to remove attributes.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3000);
            return;
        }
        dynamicAttributes.remove(attr);
    }

    @Command
    @NotifyChange({"newProduct", "dynamicAttributes"})
    public void createProduct() {
        if (!canCreateItem()) {
            Clients.showNotification("You do not have permission to add products.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            return;
        }
        if (newProduct.getSku() == null || newProduct.getSku().trim().isEmpty()) {
            Clients.showNotification("SKU is required.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        Map<String, Object> attrs = new HashMap<>();
        for (AttributeRow row : dynamicAttributes) {
            if (row.getKey() != null && !row.getKey().trim().isEmpty()) {
                attrs.put(row.getKey().trim(), row.getValue());
            }
        }
        newProduct.setAttributes(attrs);
        try {
            productService.create(newProduct);
            Clients.showNotification("Product '" + newProduct.getSku() + "' created!",
                    Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
            this.newProduct = new Product();
            this.dynamicAttributes = new ArrayList<>();
            loadProducts();
        } catch (Exception e) {
            Clients.showNotification("Failed to create product: " + e.getMessage(),
                    Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3500);
        }
    }

    @Command
    @NotifyChange({"editingProduct", "selectedProduct"})
    public void startEdit(@BindingParam("product") Product product) {
        if (!canEdit()) {
            Clients.showNotification("You do not have permission to edit product details.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            return;
        }
        this.editingProduct  = product;
        this.selectedProduct = product;
    }

    @Command
    @NotifyChange({"editingProduct", "products"})
    public void cancelEdit() {
        this.editingProduct = null;
        loadProducts();
    }

    @Command
    @NotifyChange({"products", "editingProduct", "selectedProduct"})
    public void saveProduct() {
        if (!canSave()) {
            Clients.showNotification("You do not have permission to save product edits.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            return;
        }
        Product toSave = editingProduct != null ? editingProduct : selectedProduct;
        if (toSave == null) {
            Clients.showNotification("Please select or double-click an item to edit first.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        try {
            productService.update(toSave);
            String sku = toSave.getSku();
            this.editingProduct  = null;
            this.selectedProduct = toSave;
            loadProducts();
            Clients.showNotification("Product '" + sku + "' saved!",
                    Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
        } catch (Exception e) {
            Clients.showNotification("Failed to save product: " + e.getMessage(),
                    Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3500);
        }
    }

    @Command
    @NotifyChange({"products", "selectedProduct", "editingProduct"})
    public void deleteSelected() {
        if (!canDelete()) {
            Clients.showNotification("You do not have permission to delete products.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            return;
        }
        Product toDelete = selectedProduct != null ? selectedProduct : editingProduct;
        if (toDelete == null) {
            Clients.showNotification("Please select an item to delete first.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        try {
            productService.delete(toDelete.getId());
            products.remove(toDelete);
            String sku = toDelete.getSku();
            this.selectedProduct = null;
            this.editingProduct  = null;
            Clients.showNotification("Product '" + sku + "' deleted.",
                    Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
        } catch (Exception e) {
            Clients.showNotification("Failed to delete product: " + e.getMessage(),
                    Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3500);
        }
    }

    @Command
    public void openAttributeEditor(@BindingParam("product") Product product) {
        if (!canEditAttributes()) {
            Clients.showNotification("You do not have permission to edit attributes.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            return;
        }
        Map<String, Object> args = new HashMap<>();
        args.put("product", product);
        Window win = (Window) Executions.createComponents("~./zul/attributeEditor.zul", null, args);
        win.addEventListener("onAttributesUpdated", event -> {
            loadProducts();
            BindUtils.postNotifyChange(null, null, this, "products");
        });
        win.doModal();
    }

    private void switchMainContent(Component view, String src) {
        Include mainContent = null;
        if (view != null && view.getPage() != null) {
            mainContent = (Include) view.getPage().getFellowIfAny("mainContent");
        }
        if (mainContent == null) {
            try { mainContent = (Include) Path.getComponent("/mainContent"); }
            catch (Exception ignored) {}
        }
        if (mainContent != null) {
            mainContent.setSrc(src);
        }
    }

    // ---- Getters and Setters ----
    public List<Product> getProducts()             { return products; }
    public int  getProductCount()                  { return products != null ? products.size() : 0; }
    public String getSelectedCategory()            { return selectedCategory; }
    public void   setSelectedCategory(String sc)   { this.selectedCategory = sc; }
    public boolean isLowStockOnly()                { return lowStockOnly; }
    public Product getSelectedProduct()            { return selectedProduct; }
    public void    setSelectedProduct(Product p)   { this.selectedProduct = p; }
    public Product getEditingProduct()             { return editingProduct; }
    public void    setEditingProduct(Product p)    { this.editingProduct = p; }
    public Product getNewProduct()                 { return newProduct; }
    public List<AttributeRow> getDynamicAttributes() { return dynamicAttributes; }
    public List<com.wys.inventory.model.Category> getCategories() { return categories; }
    public List<String> getFilterCategories()      { return filterCategories; }

    /** A single editable key/value row in the itemCreator.zul attributes grid. */
    public static class AttributeRow {
        private String key;
        private String value;
        public String getKey()           { return key; }
        public void   setKey(String key) { this.key = key; }
        public String getValue()             { return value; }
        public void   setValue(String value) { this.value = value; }
    }
}
