# wys-inventory-portal

**WYS — Warehouse Inventory Solutions.** A dynamic inventory & warehouse audit portal with
role-based access control (RBAC) and per-user permission overrides, built on
**Spring Boot 3 + ZK (MVVM) + PostgreSQL**.

## Stack

| Layer | Technology |
| --- | --- |
| Language / Runtime | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Data access | Spring Data JPA + Hibernate (`ddl-auto: validate`) |
| Database | PostgreSQL (JDBC driver, runtime) |
| Schema migrations | Flyway 10 (`flyway-core` + `flyway-database-postgresql`) |
| UI | ZK 10.0.0-jakarta + `zkspringboot-starter` 4.0.5 + `zkplus` |
| Security | Spring Security (form login, BCrypt) |
| JSON | Jackson (for `products.attributes` JSONB column) |
| Utilities | Guava 33.0.0-jre |

## Features

- **Public marketing landing page** (`index.zul`) with a "Launch Portal" CTA.
- **Authentication** via Spring Security form login (`/zul/login` → `/zul/app`).
- **Dynamic RBAC**: menus, actions, and page-level permissions are evaluated at
  runtime by `AccessControlService` inside each ViewModel — not hardcoded matchers.
- **Per-user permission overrides**: a Super Admin can grant or revoke individual
  menus/actions for a specific user on top of (or against) that user's role.
- **Product catalog** with dynamic per-category attributes (JSONB), inline editing,
  and an attribute editor modal.
- **Item creator** with dynamic attributes and on-the-fly category creation.
- **Stock adjustment** with audit logging and real-time dashboard refresh
  (ZK `EventQueues` + `StockChangeBridge`).
- **Audit trail** of every stock change (who, when, previous/new quantity, note).

## Architecture

```
src/main/java/com/wys/inventory/
├── controller/      PortalController          clean /zul/{page} URLs → ZK views
├── config/          SecurityConfig            Spring Security (form login, BCrypt)
├── model/           JPA entities + composite permission IDs
├── repository/      Spring Data JPA repositories
├── service/         Business logic + AccessControlService (effective permissions)
├── viewmodel/       ZK MVVM ViewModels (one per page)
├── event/           StockChangedEvent + StockChangeBridge (EventQueues)
└── util/            JSON/List converters, DynamicAttributeHelper

src/main/resources/
├── application.yml                datasource, JPA, Flyway, ZK homepage
└── web/zul/                       ZK pages (index, login, app, dashboard,
                                   catalog, itemCreator, stockAdjust,
                                   permissionAdmin, attributeEditor, profile)
```

### Request flow

1. `index.zul` is the ZK homepage (`zk.homepage: zul/index`) — a public landing page.
2. Unauthenticated requests are redirected by Spring Security to `/zul/login`.
3. `PortalController` maps clean URLs (`/zul/dashboard`, `/zul/catalog`, …) to ZK
   view names; ZK's `InternalViewResolver` resolves e.g. `zul/login` →
   `/zkau/web/zul/login.zul`.
4. `SecurityConfig` requires authentication for all app pages; **fine-grained**
   access (which menus/actions a user may use) is enforced at runtime by
   `AccessControlService` inside each ViewModel.

## Database schema

Flyway migrations live in `src/main/resources/db/migration/` and run automatically
on startup (`spring.flyway.enabled=true`, `baseline-on-migrate=true`).

| Migration | Contents |
| --- | --- |
| `V1__init_schema.sql` | All tables + indexes |
| `V2__seed_data.sql` | Roles, menus, actions, role grants, 3 default users, categories, products, audit logs |
| `V3__seed_users.sql` | 12 more users (brings total to 15 — 5 per role) |
| `V4__user_permissions.sql` | `user_menus` / `user_actions` per-user override tables |

### Tables

**Master data**
- `roles` — `id, role_code, role_name, description`
- `users` — `id, username, password_hash, email, enabled, role_id, created_at`
- `menus` — `id, menu_code, label, route_url, icon, display_order`
- `actions` — `id, action_code, action_name, description`

**Role-based grants** (baseline access per role)
- `role_menus` — `(role_id, menu_id)` composite PK
- `role_actions` — `(role_id, action_id)` composite PK

**Per-user overrides** (grant/revoke for a specific user)
- `user_menus` — `(user_id, menu_id)` PK, `granted BOOLEAN`, `created_at`
- `user_actions` — `(user_id, action_id)` PK, `granted BOOLEAN`, `created_at`

**Business data**
- `categories` — `id, name, suggested_attribute_keys` (JSON array of attribute keys)
- `products` — `id, sku, name, category, quantity, reorder_level, unit_price, attributes (JSONB), created_at, last_updated`
- `audit_logs` — `id, product_id, sku, action, change_amount, previous_quantity, new_quantity, note, performed_by, timestamp`

## Grant / revoke logic

Permissions are resolved in two layers:

1. **Role layer** — `role_menus` / `role_actions` define what a user's role allows.
2. **User override layer** — `user_menus` / `user_actions` store per-user exceptions
   with a `granted` flag (`TRUE` = grant, `FALSE` = explicit revoke).

The **effective permission** for a user is computed by `AccessControlService`:

```
effective = (roleHas && !denied) || granted
```

- A user **has** a menu/action if their role grants it **and** no user-level
  revoke (`granted = FALSE`) exists, **or** if there is an explicit user-level
  grant (`granted = TRUE`).
- A user-level **revoke** (`granted = FALSE`) therefore *removes* access that the
  role would otherwise give; a user-level **grant** *adds* access the role lacks.

`PermissionService.replaceUserPermissions(...)` reconciles the full desired
override set for a user against role inheritance (insert new overrides, delete
revoked ones). `AccessControlService` caches effective permissions per user and
exposes `invalidateForUser(username)` (via a static `ACTIVE_INSTANCES` registry)
so a save by one session is visible to others; it also cleans up on `@PreDestroy`.

## Seeded data

- **Roles**: `SUPER_ADMIN` (all menus/actions), `ADMIN` (Dashboard, Catalog,
  Stock Adjust + `STOCK_ADJUST_SUBMIT`), `MANAGER` (Dashboard, Catalog,
  Item Creator, Stock Adjust + all product/stock actions).
- **Menus**: Dashboard, Product Catalog, New Item Creator, Stock Adjustment,
  Permission Management.
- **Actions**: catalog edit/delete/save/attribute-editor, item create/add-attribute/
  add-category, stock adjust submit, permission-admin access.
- **Users**: 15 total (5 per role) — `superadmin`, `admin`, `manager` plus 4 more
  per role. **All seeded accounts share the password `your_password`** (single shared
  BCrypt hash). Change these before any real deployment.
- **Sample data**: 4 categories, 4 products, 4 audit-log entries.

## Getting started

**Prerequisites:** Java 17+, Maven (or use the wrapper), and a running PostgreSQL
with a database named `inventory_portal`.

1. (Optional) Create the database:
   ```sql
   CREATE DATABASE inventory_portal;
   ```
2. Review/adjust the datasource in `src/main/resources/application.yml`
   (defaults: `jdbc:postgresql://localhost:5432/inventory_portal`,
   user `postgres`, password `your_password`).
3. Run the app — Flyway creates the schema and seeds data automatically:
   ```bash
   ./mvnw spring-boot:run        # or: mvn spring-boot:run
   ```
4. Open <http://localhost:8080> → **Launch Portal** → log in with a seeded user
   (e.g. `superadmin` / `12345`).

## Tests

```bash
./mvnw test
```

Unit tests cover permission identity (`PermissionIdentityTest`), product service
logic (`ProductServiceTest`), and stock service logic (`StockServiceTest`).

## Notes

- `spring.jpa.hibernate.ddl-auto` is set to `validate` — the schema is owned by
  Flyway; Hibernate only checks that entities match it.
- CSRF is disabled and `JSESSIONID` is deleted on logout (see `SecurityConfig`).
- The `products.attributes` column is JSONB, serialized/deserialized via Jackson
  (`JsonMapConverter`).
