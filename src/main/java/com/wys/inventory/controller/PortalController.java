package com.wys.inventory.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Maps clean /zul/{page} URLs to ZK view names.
 *
 * WHY this is needed:
 *   ZK's DHtmlLayoutServlet only intercepts *.zul and *.zhtml URL patterns.
 *   Clean URLs like /zul/login (no extension) have NO handler unless mapped here.
 *   ZK's InternalViewResolver converts e.g. "zul/login" -> /zkau/web/zul/login.zul.
 *
 * WHY GET "/" is ABSENT:
 *   ZkHomepageConfiguration already owns GET / (configured via zk.homepage in application.yml).
 *   Mapping GET / here too causes "Ambiguous mapping" startup failure.
 */
@Controller
public class PortalController {

    /** Login page — Spring Security redirects here on unauthenticated requests. */
    @GetMapping({"/zul/login", "/login"})
    public String loginPage() {
        return "zul/login";
    }

    @GetMapping({"/zul/index", "/index"})
    public String indexPage() {
        return "zul/index";
    }

    /** Main app shell — protected; only accessible to ADMIN or MANAGER. */
    @GetMapping({"/zul/app", "/app"})
    public String appPage() {
        return "zul/app";
    }

    /** Dashboard (embedded inside app shell via ZK include). */
    @GetMapping("/zul/dashboard")
    public String dashboardPage() {
        return "zul/dashboard";
    }

    /** Product catalog. */
    @GetMapping("/zul/catalog")
    public String catalogPage() {
        return "zul/catalog";
    }

    /** Stock adjustment. */
    @GetMapping("/zul/stockAdjust")
    public String stockAdjustPage() {
        return "zul/stockAdjust";
    }

    /** Item creator — MANAGER role only (also enforced in SecurityConfig). */
    @GetMapping("/zul/itemCreator")
    public String itemCreatorPage() {
        return "zul/itemCreator";
    }

    /**
     * Permission management — SUPER_ADMIN only.
     * The guard itself lives in PermissionAdminViewModel (PERM_ADMIN_ACCESS), so this
     * mapping stays reachable for non-superadmins and simply renders the denied state.
     */
    @GetMapping({"/zul/permissionAdmin", "/zul/permissions"})
    public String permissionAdminPage() {
        return "zul/permissionAdmin";
    }
}