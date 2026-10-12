-- =====================================================================
-- V1__init_schema.sql
-- WYS Inventory Portal Full PostgreSQL Schema
-- =====================================================================

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,
    role_name   VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(150) UNIQUE,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    role_id       BIGINT REFERENCES roles(id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE menus (
    id            BIGSERIAL PRIMARY KEY,
    menu_code     VARCHAR(100) NOT NULL UNIQUE,
    label         VARCHAR(100) NOT NULL,
    route_url     VARCHAR(200) NOT NULL,
    icon          VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE actions (
    id          BIGSERIAL PRIMARY KEY,
    action_code VARCHAR(100) NOT NULL UNIQUE,
    action_name VARCHAR(150) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE role_menus (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    menu_id BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE role_actions (
    role_id   BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    action_id BIGINT NOT NULL REFERENCES actions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, action_id)
);

CREATE TABLE categories (
    id                       BIGSERIAL PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL UNIQUE,
    suggested_attribute_keys TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    sku           VARCHAR(100) NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    category      VARCHAR(100),
    quantity      INTEGER       NOT NULL DEFAULT 0,
    reorder_level INTEGER       NOT NULL DEFAULT 0,
    unit_price    NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    attributes    JSONB         NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_updated  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT REFERENCES products(id) ON DELETE SET NULL,
    sku               VARCHAR(100),
    action            VARCHAR(50) NOT NULL,
    change_amount     INTEGER     NOT NULL DEFAULT 0,
    previous_quantity INTEGER     NOT NULL DEFAULT 0,
    new_quantity      INTEGER     NOT NULL DEFAULT 0,
    note              TEXT,
    performed_by      VARCHAR(100),
    timestamp         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category  ON products(category);
CREATE INDEX idx_products_sku       ON products(sku);
CREATE INDEX idx_audit_logs_product ON audit_logs(product_id);
CREATE INDEX idx_users_username     ON users(username);
