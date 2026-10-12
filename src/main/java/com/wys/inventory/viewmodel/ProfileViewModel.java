package com.wys.inventory.viewmodel;

import com.wys.inventory.model.User;
import com.wys.inventory.service.AccessControlService;
import com.wys.inventory.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.spring.DelegatingVariableResolver;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * ViewModel for profile.zul — displays the authenticated user's own profile data
 * fetched directly from the database (Name, ID, Role, Email, Account Status, Created At).
 */
@VariableResolver(DelegatingVariableResolver.class)
public class ProfileViewModel {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @org.zkoss.zk.ui.select.annotation.WireVariable
    private UserService userService;

    @org.zkoss.zk.ui.select.annotation.WireVariable
    private AccessControlService accessControlService;

    private String username;
    private Long   userId;
    private String roleCode;
    private String roleName;
    private String email;
    private boolean enabled;
    private Instant createdAt;
    private String  avatarInitial;
    private boolean  loaded = false;

    @Init
    public void init() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            this.username = auth.getName();
            this.roleCode = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .findFirst()
                    .orElse("USER");
        }
        loadProfile();
    }

    @Command
    @NotifyChange("*")
    public void loadProfile() {
        if (userService != null && username != null) {
            User user = userService.findByUsername(username);
            if (user != null) {
                this.userId     = user.getId();
                this.roleName   = user.getRole() != null ? user.getRole().getRoleName() : "(no role)";
                this.roleCode   = user.getRoleCode();
                this.email      = user.getEmail();
                this.enabled    = user.isEnabled();
                this.createdAt  = user.getCreatedAt();
                this.avatarInitial = user.getUsername().substring(0, 1).toUpperCase();
            }
        }
        this.loaded = true;
    }

    /** Refresh user data from DB. */
    @Command
    @NotifyChange("*")
    public void refresh() {
        loadProfile();
        Clients.showNotification("Profile refreshed.", Clients.NOTIFICATION_TYPE_INFO, null, "top_center", 2000);
    }

    // ---- Getters for ZUL ----

    public String getUsername()        { return username; }
    public Long   getUserId()          { return userId; }
    public String getRoleCode()        { return roleCode; }
    public String getRoleName()        { return roleName; }
    public String getEmail()           { return email; }
    public boolean isEnabled()         { return enabled; }
    public String getCreatedAt()       { return createdAt != null ? DATE_FORMAT.format(createdAt) : "—"; }
    public String getAvatarInitial()   { return avatarInitial; }
    public boolean isLoaded()          { return loaded; }

    public String getRoleBadgeClass() {
        if ("SUPER_ADMIN".equalsIgnoreCase(roleCode)) return "badge badge-warning";
        if ("ADMIN".equalsIgnoreCase(roleCode))       return "badge badge-info";
        if ("MANAGER".equalsIgnoreCase(roleCode))     return "badge badge-success";
        return "badge badge-neutral";
    }

    public String getAccountStatusClass() {
        return enabled ? "badge badge-success" : "badge badge-danger";
    }

    public String getAccountStatusText() {
        return enabled ? "Active" : "Disabled";
    }
}