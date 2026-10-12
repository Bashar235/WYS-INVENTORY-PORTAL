package com.wys.inventory.viewmodel;

import com.wys.inventory.model.Product;
import com.wys.inventory.service.AccessControlService;
import com.wys.inventory.service.ProductService;
import com.wys.inventory.util.DynamicAttributeHelper;
import com.wys.inventory.util.DynamicAttributeHelper.AttributeRow;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Path;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zul.Include;
import org.zkoss.zul.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for itemCreator.zul.
 * Page-level access is guarded dynamically via AccessControlService.
 */
@VariableResolver(DelegatingVariableResolver.class)
public class ItemCreatorViewModel {

    @WireVariable
    private ProductService productService;

    @WireVariable("categoryService")
    private com.wys.inventory.service.CategoryService categoryService;

    @WireVariable
    private AccessControlService accessControlService;

    private Product product = new Product();
    private List<AttributeRow> attributeRows = new ArrayList<>();
    private List<com.wys.inventory.model.Category> categories = new ArrayList<>();
    private String newCategoryName;
    private Component view;
    private String errorMessage;

    @Init
    public void init(@ContextParam(ContextType.VIEW) Component view) {
        this.view = view;
        // Dynamic page guard: redirect if user lacks ITEM_CREATE_SUBMIT permission
        if (!accessControlService.canDo("ITEM_CREATE_SUBMIT")) {
            Clients.showNotification("You do not have permission to create products.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            navigateToCatalog();
            return;
        }
        loadCategories();
        attributeRows.add(new AttributeRow("warrantyMonths", "12"));
    }

    private void loadCategories() {
        this.categories = categoryService.findAll();
    }

    @Command
    @NotifyChange({"categories", "newCategoryName", "product"})
    public void addCategory() {
        if (!accessControlService.canDo("ITEM_ADD_CATEGORY")) {
            Clients.showNotification("You do not have permission to add categories.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        if (newCategoryName == null || newCategoryName.trim().isEmpty()) {
            Clients.showNotification("Category name cannot be empty.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        try {
            com.wys.inventory.model.Category created = categoryService.create(newCategoryName);
            loadCategories();
            product.setCategory(created.getName());
            newCategoryName = "";
            Clients.showNotification("Category added!", Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
        } catch (Exception e) {
            Clients.showNotification(e.getMessage(), Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3000);
        }
    }

    @Command
    @NotifyChange("attributeRows")
    public void addAttr() {
        if (!accessControlService.canDo("ITEM_ADD_ATTRIBUTE")) {
            Clients.showNotification("You do not have permission to add attributes.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        attributeRows.add(new AttributeRow("", ""));
    }

    @Command
    @NotifyChange("attributeRows")
    public void removeAttr(@BindingParam("row") AttributeRow row) {
        if (!accessControlService.canDo("ITEM_ADD_ATTRIBUTE")) {
            Clients.showNotification("You do not have permission to remove attributes.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        attributeRows.remove(row);
    }

    @Command
    public void save() {
        if (!accessControlService.canDo("ITEM_CREATE_SUBMIT")) {
            Clients.showNotification("You do not have permission to create products.",
                    Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 3500);
            navigateToCatalog();
            return;
        }
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            Clients.showNotification("SKU is required.", Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            Clients.showNotification("Product name is required.", Clients.NOTIFICATION_TYPE_WARNING, null, "top_center", 2500);
            return;
        }
        try {
            product.setAttributes(DynamicAttributeHelper.toMap(attributeRows));
            productService.create(product);
            Clients.showNotification("Product '" + product.getName() + "' created successfully!",
                    Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
            Events.postEvent("onItemCreated", view, null);
            navigateToCatalog();
        } catch (Exception e) {
            Clients.showNotification(e.getMessage(), Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3000);
        }
    }

    @Command
    public void cancel() { navigateToCatalog(); }

    private void navigateToCatalog() {
        Include mainContent = null;
        if (view != null && view.getPage() != null) {
            mainContent = (Include) view.getPage().getFellowIfAny("mainContent");
        }
        if (mainContent == null) {
            try { mainContent = (Include) Path.getComponent("/mainContent"); }
            catch (Exception ignored) {}
        }
        if (mainContent != null) {
            mainContent.setSrc("~./zul/catalog.zul");
        } else if (view instanceof Window) {
            ((Window) view).detach();
        }
    }

    // Getters and Setters
    public Product             getProduct()          { return product; }
    public List<AttributeRow>  getAttributeRows()    { return attributeRows; }
    public List<com.wys.inventory.model.Category> getCategories() { return categories; }
    public String  getNewCategoryName()              { return newCategoryName; }
    public void    setNewCategoryName(String n)      { this.newCategoryName = n; }
    public String  getErrorMessage()                 { return errorMessage; }
}
