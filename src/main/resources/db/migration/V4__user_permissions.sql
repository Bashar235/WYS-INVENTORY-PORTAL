-- =====================================================================
-- V4__user_permissions.sql
-- User-specific permission overrides (user_menus, user_actions)
-- Allows Super Admin to grant/revoke permissions per individual user
-- in addition to role-based permissions.
-- =====================================================================

-- ---- User-Menu Overrides ----
CREATE TABLE user_menus (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    menu_id BIGINT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
    granted BOOLEAN NOT NULL DEFAULT TRUE,  -- TRUE = grant, FALSE = explicit revoke
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, menu_id)
);

CREATE INDEX idx_user_menus_user ON user_menus(user_id);
CREATE INDEX idx_user_menus_menu ON user_menus(menu_id);

-- ---- User-Action Overrides ----
CREATE TABLE user_actions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action_id BIGINT NOT NULL REFERENCES actions(id) ON DELETE CASCADE,
    granted BOOLEAN NOT NULL DEFAULT TRUE,  -- TRUE = grant, FALSE = explicit revoke
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, action_id)
);

CREATE INDEX idx_user_actions_user ON user_actions(user_id);
CREATE INDEX idx_user_actions_action ON user_actions(action_id);

-- ---- Comments ----
COMMENT ON TABLE user_menus IS 'User-specific menu permission overrides. granted=TRUE means allow, FALSE means explicit deny (overrides role).';
COMMENT ON TABLE user_actions IS 'User-specific action permission overrides. granted=TRUE means allow, FALSE means explicit deny (overrides role).';
COMMENT ON COLUMN user_menus.granted IS 'TRUE = grant this menu to user, FALSE = explicitly revoke this menu from user (even if role has it)';
COMMENT ON COLUMN user_actions.granted IS 'TRUE = grant this action to user, FALSE = explicitly revoke this action from user (even if role has it)';