-- =====================================================================
-- V2__seed_data.sql
-- WYS Inventory Portal Seed Data
-- Roles, Menus, Actions, Users, Role-Menu/Action assignments, Products
-- =====================================================================

-- ---- Roles ----
INSERT INTO roles (role_code, role_name, description) VALUES
  ('SUPER_ADMIN', 'Super Administrator',  'Full system access including permission management'),
  ('ADMIN',       'Administrator',        'Read-only catalog access, stock visibility'),
  ('MANAGER',     'Warehouse Manager',    'Full product and stock management');

-- ---- Menus ----
INSERT INTO menus (menu_code, label, route_url, icon, display_order) VALUES
  ('MENU_DASHBOARD',    'Dashboard Overview',    '~./zul/dashboard.zul',       'z-icon-dashboard',  1),
  ('MENU_CATALOG',      'Product Catalog',       '~./zul/catalog.zul',         'z-icon-list',       2),
  ('MENU_ITEM_CREATOR', 'New Item Creator',      '~./zul/itemCreator.zul',     'z-icon-plus',       3),
  ('MENU_STOCK_ADJUST', 'Stock Adjustment',      '~./zul/stockAdjust.zul',     'z-icon-adjust',     4),
  ('MENU_PERM_ADMIN',   'Permission Management', '~./zul/permissionAdmin.zul', 'z-icon-shield',     5);

-- ---- Actions ----
INSERT INTO actions (action_code, action_name, description) VALUES
  ('CATALOG_DOUBLECLICK_EDIT',  'Edit Product on Double-Click',  'Allows inline editing of product details via double-click'),
  ('CATALOG_SINGLECLICK_DELETE','Delete Product',                'Allows deleting a selected product'),
  ('CATALOG_SAVE_PRODUCT',      'Save Product Edits',            'Allows saving inline catalog edits'),
  ('CATALOG_OPEN_ATTR_EDITOR',  'Open Attribute Editor',         'Allows opening the attribute editor modal'),
  ('ITEM_CREATE_SUBMIT',        'Create New Product',            'Allows submitting the new item creation form'),
  ('ITEM_ADD_ATTRIBUTE',        'Add Dynamic Attribute',         'Allows adding dynamic attributes to a new product'),
  ('ITEM_ADD_CATEGORY',         'Add New Category',              'Allows creating a new product category'),
  ('STOCK_ADJUST_SUBMIT',       'Submit Stock Adjustment',       'Allows submitting a stock quantity change'),
  ('PERM_ADMIN_ACCESS',         'Access Permission Management',  'Allows viewing and editing role permissions');

-- ---- Role-Menu Assignments ----
-- SUPER_ADMIN: all menus
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m WHERE r.role_code = 'SUPER_ADMIN';

-- ADMIN: Dashboard, Catalog, Stock Adjust
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m
WHERE r.role_code = 'ADMIN'
  AND m.menu_code IN ('MENU_DASHBOARD', 'MENU_CATALOG', 'MENU_STOCK_ADJUST');

-- MANAGER: Dashboard, Catalog, Item Creator, Stock Adjust
INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id FROM roles r, menus m
WHERE r.role_code = 'MANAGER'
  AND m.menu_code IN ('MENU_DASHBOARD', 'MENU_CATALOG', 'MENU_ITEM_CREATOR', 'MENU_STOCK_ADJUST');

-- ---- Role-Action Assignments ----
-- SUPER_ADMIN: all actions
INSERT INTO role_actions (role_id, action_id)
SELECT r.id, a.id FROM roles r, actions a WHERE r.role_code = 'SUPER_ADMIN';

-- ADMIN: read-only, can only view (no write actions except stock)
INSERT INTO role_actions (role_id, action_id)
SELECT r.id, a.id FROM roles r, actions a
WHERE r.role_code = 'ADMIN'
  AND a.action_code IN ('STOCK_ADJUST_SUBMIT');

-- MANAGER: all product + stock actions (no permission admin)
INSERT INTO role_actions (role_id, action_id)
SELECT r.id, a.id FROM roles r, actions a
WHERE r.role_code = 'MANAGER'
  AND a.action_code IN (
    'CATALOG_DOUBLECLICK_EDIT', 'CATALOG_SINGLECLICK_DELETE', 'CATALOG_SAVE_PRODUCT',
    'CATALOG_OPEN_ATTR_EDITOR', 'ITEM_CREATE_SUBMIT', 'ITEM_ADD_ATTRIBUTE',
    'ITEM_ADD_CATEGORY', 'STOCK_ADJUST_SUBMIT'
  );

-- ---- Default Users ----
-- Passwords: superadmin=superadmin123, admin=admin123, manager=manager123 (BCrypt $2a$10$)
INSERT INTO users (username, password_hash, email, enabled, role_id) VALUES
  ('superadmin', '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m',
   'superadmin@wys.local', TRUE,
   (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN')),
  ('admin', '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m',
   'admin@wys.local', TRUE,
   (SELECT id FROM roles WHERE role_code = 'ADMIN')),
  ('manager', '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m',
   'manager@wys.local', TRUE,
   (SELECT id FROM roles WHERE role_code = 'MANAGER'));

-- ---- Sample Categories ----
INSERT INTO categories (name, suggested_attribute_keys) VALUES
  ('Electronics',  '["warrantyMonths","serialNumberPrefix"]'),
  ('Perishable',   '["batchNumber","expirationDate"]'),
  ('Office',       '["brand","model"]'),
  ('Furniture',    '["material","dimensions"]');

-- ---- Sample Products ----
INSERT INTO products (sku, name, category, quantity, reorder_level, unit_price, attributes) VALUES
  ('ELEC-0001', 'Wireless Mouse',   'Electronics', 120, 20, 15.99,
   '{"warrantyMonths": 12, "serialNumberPrefix": "WM-2026"}'),
  ('FOOD-0021', 'Organic Milk 1L',  'Perishable',  8,  15, 2.49,
   '{"batchNumber": "B20260901", "expirationDate": "2026-10-05"}'),
  ('OFFIC-001', 'A4 Paper Ream',    'Office',      200, 50, 4.99,
   '{"brand": "OfficePro", "sheets": 500}'),
  ('FURN-0042', 'Ergonomic Chair',  'Furniture',   15,  5, 299.99,
   '{"material": "Mesh", "dimensions": "60x60x120cm"}');

-- ---- Sample Audit Logs ----
INSERT INTO audit_logs (product_id, sku, action, change_amount, previous_quantity, new_quantity, note, performed_by, timestamp) VALUES
  ((SELECT id FROM products WHERE sku = 'ELEC-0001'), 'ELEC-0001', 'INITIAL_IMPORT', 120, 0, 120, 'Initial warehouse consignment delivery', 'superadmin', NOW() - INTERVAL '2 hours'),
  ((SELECT id FROM products WHERE sku = 'FOOD-0021'), 'FOOD-0021', 'STOCK_IN', 8, 0, 8, 'Perishable dairy batch intake [Batch B20260901]', 'manager', NOW() - INTERVAL '90 minutes'),
  ((SELECT id FROM products WHERE sku = 'OFFIC-001'), 'OFFIC-001', 'INITIAL_IMPORT', 200, 0, 200, 'Pallet delivery from supplier', 'manager', NOW() - INTERVAL '45 minutes'),
  ((SELECT id FROM products WHERE sku = 'FURN-0042'), 'FURN-0042', 'STOCK_ADJUSTMENT', 5, 10, 15, 'Received warranty replacement stock', 'superadmin', NOW() - INTERVAL '15 minutes');

