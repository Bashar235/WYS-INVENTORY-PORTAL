package com.wys.inventory.viewmodel;

import com.wys.inventory.model.Product;
import com.wys.inventory.service.ProductService;
import com.wys.inventory.util.DynamicAttributeHelper;
import com.wys.inventory.util.DynamicAttributeHelper.AttributeRow;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;
import org.zkoss.zul.Window;

import java.util.ArrayList;
import java.util.List;

@VariableResolver(DelegatingVariableResolver.class)
public class AttributeEditorViewModel {

    @WireVariable
    private ProductService productService;

    private Product product;
    private List<AttributeRow> attributeRows = new ArrayList<>();
    private Component view;

    @Init
    public void init(@ContextParam(ContextType.VIEW) Component view) {
        this.view = view;
        Execution exec = Executions.getCurrent();
        this.product = (Product) exec.getArg().get("product");
        if (product != null && product.getAttributes() != null) {
            this.attributeRows = DynamicAttributeHelper.toRows(product.getAttributes());
        }
        if (this.attributeRows.isEmpty()) {
            this.attributeRows.add(new AttributeRow("", ""));
        }
    }

    @Command
    @NotifyChange("attributeRows")
    public void addRow() {
        attributeRows.add(new AttributeRow("", ""));
    }

    @Command
    @NotifyChange("attributeRows")
    public void removeRow(@BindingParam("row") AttributeRow row) {
        attributeRows.remove(row);
    }

    @Command
    public void save() {
        if (product == null) {
            return;
        }
        try {
            product.setAttributes(DynamicAttributeHelper.toMap(attributeRows));
            productService.update(product);
            Clients.showNotification("Attributes updated for " + product.getSku(), Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
            Events.postEvent("onAttributesUpdated", view, null);
            if (view instanceof Window) {
                ((Window) view).detach();
            }
        } catch (Exception e) {
            Clients.showNotification("Error saving attributes: " + e.getMessage(), Clients.NOTIFICATION_TYPE_ERROR, null, "top_center", 3000);
        }
    }

    @Command
    public void cancel() {
        if (view instanceof Window) {
            ((Window) view).detach();
        }
    }

    public Product getProduct() { return product; }
    public List<AttributeRow> getAttributeRows() { return attributeRows; }
}
