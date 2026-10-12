package com.wys.inventory.viewmodel;

import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

@VariableResolver(DelegatingVariableResolver.class)
public class LoginViewModel {

    private String errorMessage;
    private String infoMessage;

    @Init
    public void init() {
        // Check for logout or error parameters from URL
        String error = Executions.getCurrent().getParameter("error");
        String logout = Executions.getCurrent().getParameter("logout");

        if ("true".equals(error)) {
            this.errorMessage = "Invalid username or password. Please try again.";
        }
        if ("true".equals(logout)) {
            this.infoMessage = "You have been logged out successfully.";
        }
    }

    // Getters & Setters with NotifyChange for ZK Data Binding
    public String getErrorMessage() { 
        return errorMessage; 
    }

    @NotifyChange("errorMessage")
    public void setErrorMessage(String errorMessage) { 
        this.errorMessage = errorMessage; 
    }

    public String getInfoMessage() { 
        return infoMessage; 
    }

    @NotifyChange("infoMessage")
    public void setInfoMessage(String infoMessage) { 
        this.infoMessage = infoMessage; 
    }
}