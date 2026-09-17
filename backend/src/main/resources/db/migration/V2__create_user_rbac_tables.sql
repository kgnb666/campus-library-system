-- ======================================================================
-- V2__create_user_rbac_tables.sql
-- Stage 1-B: 用户身份体系与 RBAC 权限基础数据库表
-- ======================================================================

-- 1. 用户表 (users)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

COMMENT ON TABLE users IS '系统用户基础信息表';
COMMENT ON COLUMN users.id IS '用户全局唯一主键 ID';
COMMENT ON COLUMN users.username IS '登录用户名，唯一';
COMMENT ON COLUMN users.email IS '电子邮箱，唯一';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 加密密码哈希，禁止明文';
COMMENT ON COLUMN users.nickname IS '用户昵称/显示名称';
COMMENT ON COLUMN users.avatar_url IS '用户头像 URL';
COMMENT ON COLUMN users.status IS '账号状态: ACTIVE-正常, DISABLED-禁用';
COMMENT ON COLUMN users.created_at IS '账号创建时间';
COMMENT ON COLUMN users.updated_at IS '最后更新时间';

-- 2. 角色表 (roles)
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

COMMENT ON TABLE roles IS '系统角色表';
COMMENT ON COLUMN roles.id IS '角色主键 ID';
COMMENT ON COLUMN roles.code IS '角色唯一英文编码 (如 STUDENT, LIBRARIAN, ADMIN)';
COMMENT ON COLUMN roles.name IS '角色中文名称';
COMMENT ON COLUMN roles.description IS '角色描述';
COMMENT ON COLUMN roles.created_at IS '创建时间';

-- 3. 权限表 (permissions)
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

COMMENT ON TABLE permissions IS '系统基础权限表';
COMMENT ON COLUMN permissions.id IS '权限主键 ID';
COMMENT ON COLUMN permissions.code IS '权限唯一编码 (如 user:profile:view)';
COMMENT ON COLUMN permissions.name IS '权限中文名称';
COMMENT ON COLUMN permissions.description IS '权限描述';
COMMENT ON COLUMN permissions.created_at IS '创建时间';

-- 4. 用户-角色关联表 (user_roles)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

COMMENT ON TABLE user_roles IS '用户与角色多对多关联表';
COMMENT ON COLUMN user_roles.user_id IS '关联用户 ID';
COMMENT ON COLUMN user_roles.role_id IS '关联角色 ID';

-- 5. 角色-权限关联表 (role_permissions)
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS '角色与权限多对多关联表';
COMMENT ON COLUMN role_permissions.role_id IS '关联角色 ID';
COMMENT ON COLUMN role_permissions.permission_id IS '关联权限 ID';

-- ======================================================================
-- 初始化角色基础数据 (STUDENT, LIBRARIAN, ADMIN)
-- ======================================================================
INSERT INTO roles (code, name, description) VALUES
    ('STUDENT', '学生', '普通在校学生读者'),
    ('LIBRARIAN', '图书管理员', '图书馆业务管理人员'),
    ('ADMIN', '系统管理员', '系统超级管理员')
ON CONFLICT (code) DO NOTHING;

-- ======================================================================
-- 初始化用户核心基础权限 (严禁添加图书与借还权限)
-- ======================================================================
INSERT INTO permissions (code, name, description) VALUES
    ('user:profile:view', '查看个人信息', '允许查看当前登录用户个人资料'),
    ('user:profile:update', '修改个人信息', '允许修改当前登录用户个人资料'),
    ('user:manage', '用户管理', '允许检索与管理用户列表及状态'),
    ('role:manage', '角色管理', '允许配置与分配用户角色')
ON CONFLICT (code) DO NOTHING;

-- ======================================================================
-- 初始化角色与权限绑定关系
-- ======================================================================
-- 1) STUDENT 角色赋权 (基础个人资料读写)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'STUDENT'
  AND p.code IN ('user:profile:view', 'user:profile:update')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2) LIBRARIAN 角色赋权 (基础个人资料读写，业务权限后续阶段补充)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'LIBRARIAN'
  AND p.code IN ('user:profile:view', 'user:profile:update')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 3) ADMIN 角色赋权 (拥有全部基础权限)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('user:profile:view', 'user:profile:update', 'user:manage', 'role:manage')
ON CONFLICT (role_id, permission_id) DO NOTHING;
