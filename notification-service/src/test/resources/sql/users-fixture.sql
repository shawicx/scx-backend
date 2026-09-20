CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE,
    "deletedAt" TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_roles (
    id TEXT PRIMARY KEY,
    "userId" CHAR(26) NOT NULL,
    "roleId" CHAR(26) NOT NULL
);
DELETE FROM user_roles;
DELETE FROM users;
INSERT INTO users (id, email, "isActive", "deletedAt") VALUES
('U_ACTIVE_1', 'a1@test.dev', TRUE, NULL),
('U_ACTIVE_2', 'a2@test.dev', TRUE, NULL),
('U_INACTIVE', 'off@test.dev', FALSE, NULL),
('U_DELETED', 'del@test.dev', TRUE, CURRENT_TIMESTAMP);
INSERT INTO user_roles (id, "userId", "roleId") VALUES
('R1', 'U_ACTIVE_1', 'ROLE_A'),
('R2', 'U_ACTIVE_2', 'ROLE_A'),
('R3', 'U_ACTIVE_1', 'ROLE_B');
