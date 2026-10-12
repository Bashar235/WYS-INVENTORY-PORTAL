-- =====================================================================
-- V3__seed_users.sql
-- WYS Inventory Portal — Additional User Seeding
-- Brings the system to 15 users (5 per role) on top of the 3 default
-- users already inserted by V2__seed_data.sql.
--
-- All new users share the password '12345' (BCrypt $2y$12$ hash).
-- =====================================================================

-- ---- SUPER_ADMIN role (4 new — 'superadmin' already exists from V2) ----
INSERT INTO users (username, password_hash, email, enabled, role_id) VALUES
  ('alex_vance',      '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'alex.vance@wys.local',      TRUE,
   (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN')),
  ('marcus_stone',    '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'marcus.stone@wys.local',    TRUE,
   (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN')),
  ('elena_rodriguez', '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'elena.rodriguez@wys.local', TRUE,
   (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN')),
  ('david_chen',      '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'david.chen@wys.local',      TRUE,
   (SELECT id FROM roles WHERE role_code = 'SUPER_ADMIN'));

-- ---- ADMIN role (4 new — 'admin' already exists from V2) ----
INSERT INTO users (username, password_hash, email, enabled, role_id) VALUES
  ('sarah_jenkins',   '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'sarah.jenkins@wys.local',   TRUE,
   (SELECT id FROM roles WHERE role_code = 'ADMIN')),
  ('james_wilson',    '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'james.wilson@wys.local',    TRUE,
   (SELECT id FROM roles WHERE role_code = 'ADMIN')),
  ('priya_sharma',    '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'priya.sharma@wys.local',    TRUE,
   (SELECT id FROM roles WHERE role_code = 'ADMIN')),
  ('michael_brown',   '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'michael.brown@wys.local',   TRUE,
   (SELECT id FROM roles WHERE role_code = 'ADMIN'));

-- ---- MANAGER role (4 new — 'manager' already exists from V2) ----
INSERT INTO users (username, password_hash, email, enabled, role_id) VALUES
  ('emily_davis',     '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'emily.davis@wys.local',     TRUE,
   (SELECT id FROM roles WHERE role_code = 'MANAGER')),
  ('robert_taylor',   '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'robert.taylor@wys.local',   TRUE,
   (SELECT id FROM roles WHERE role_code = 'MANAGER')),
  ('lisa_anderson',   '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'lisa.anderson@wys.local',   TRUE,
   (SELECT id FROM roles WHERE role_code = 'MANAGER')),
  ('kevin_martinez',  '$2y$12$dBKrx0MxfF3bjR9AP45ZlOE11GQYD17sIAVY2z3byQEpRPGqCrQ8m', 'kevin.martinez@wys.local',  TRUE,
   (SELECT id FROM roles WHERE role_code = 'MANAGER'));