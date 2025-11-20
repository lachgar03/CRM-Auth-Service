-- ============================================================
-- GLOBAL TABLES (No tenant_id) - Schema: public
-- ============================================================

-- Tenants Table
CREATE TABLE IF NOT EXISTS tenants (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL,
    subdomain VARCHAR(63) NOT NULL UNIQUE,
    subscription_plan VARCHAR(50) NOT NULL DEFAULT 'FREE',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT chk_subdomain_format CHECK (subdomain ~* '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
    CONSTRAINT chk_subscription_plan CHECK (subscription_plan IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE')),
    CONSTRAINT chk_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'PROVISIONING_FAILED'))
    );

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenants_subdomain_lower ON tenants(LOWER(subdomain));
CREATE INDEX IF NOT EXISTS idx_tenants_status ON tenants(status);

-- Roles Table (Global)
CREATE TABLE IF NOT EXISTS roles (
                                     id BIGSERIAL PRIMARY KEY,
                                     name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_system_role BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT chk_role_name CHECK (name ~ '^ROLE_[A-Z_]+$')
    );

-- Permissions Table (Global)
CREATE TABLE IF NOT EXISTS permissions (
                                           id BIGSERIAL PRIMARY KEY,
                                           name VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_permission_name CHECK (name ~ '^[A-Z_]+$'),
    CONSTRAINT chk_action CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'MANAGE')),
    CONSTRAINT unique_resource_action UNIQUE (resource, action)
    );

-- Role-Permission Mapping (Global)
CREATE TABLE IF NOT EXISTS role_permissions (
                                                role_id BIGINT NOT NULL,
                                                permission_id BIGINT NOT NULL,
                                                granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
    );

-- ============================================================
-- TENANT-SPECIFIC TABLES (WITH tenant_id)
-- ============================================================

-- Users Table
CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_non_expired BOOLEAN NOT NULL DEFAULT true,
    account_non_locked BOOLEAN NOT NULL DEFAULT true,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT unique_email_per_tenant UNIQUE(tenant_id, email)
    );

-- User Roles Mapping
CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id BIGINT NOT NULL,
                                          role_id BIGINT NOT NULL,
                                          PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

-- Customers Table
CREATE TABLE IF NOT EXISTS customers (
                                         id BIGSERIAL PRIMARY KEY,
                                         tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    company_name VARCHAR(255),
    assigned_to_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
    );

-- Invoices Table
CREATE TABLE IF NOT EXISTS invoices (
                                        id BIGSERIAL PRIMARY KEY,
                                        tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    invoice_number VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    amount_due NUMERIC(12, 2) NOT NULL,
    amount_paid NUMERIC(12, 2) DEFAULT 0.00,
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT unique_invoice_number_per_tenant UNIQUE(tenant_id, invoice_number)
    );

-- Orders Table
CREATE TABLE IF NOT EXISTS orders (
                                      id BIGSERIAL PRIMARY KEY,
                                      tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    order_number VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12, 2) NOT NULL,
    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT unique_order_number_per_tenant UNIQUE(tenant_id, order_number)
    );

-- Tickets Table
CREATE TABLE IF NOT EXISTS tickets (
                                       id BIGSERIAL PRIMARY KEY,
                                       tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    subject VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(50) DEFAULT 'MEDIUM',
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    agent_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    resolved_at TIMESTAMP
    );

-- ============================================================
-- SEED DATA: Default Roles and Permissions (SAFE INSERT)
-- ============================================================

DO $$
DECLARE
super_admin_id BIGINT;
    admin_id BIGINT;
    user_id BIGINT;
    agent_id BIGINT;
    sales_id BIGINT;
BEGIN
    -- 1. Insert Roles
INSERT INTO roles (name, description, is_system_role) VALUES
                                                          ('ROLE_SUPER_ADMIN', 'Super administrator', true),
                                                          ('ROLE_ADMIN', 'Tenant administrator', true),
                                                          ('ROLE_USER', 'Standard user', true),
                                                          ('ROLE_AGENT', 'Support agent', true),
                                                          ('ROLE_SALES', 'Sales representative', true)
    ON CONFLICT (name) DO NOTHING;

-- 2. Insert Permissions
INSERT INTO permissions (name, resource, action, description) VALUES
                                                                  -- User Management
                                                                  ('USER_CREATE', 'USER', 'CREATE', 'Create new users'),
                                                                  ('USER_READ', 'USER', 'READ', 'View user details'),
                                                                  ('USER_UPDATE', 'USER', 'UPDATE', 'Update user information'),
                                                                  ('USER_DELETE', 'USER', 'DELETE', 'Delete users'),
                                                                  ('USER_MANAGE', 'USER', 'MANAGE', 'Full user management'),

                                                                  -- Customer Management
                                                                  ('CUSTOMER_CREATE', 'CUSTOMER', 'CREATE', 'Create customers'),
                                                                  ('CUSTOMER_READ', 'CUSTOMER', 'READ', 'View customers'),
                                                                  ('CUSTOMER_UPDATE', 'CUSTOMER', 'UPDATE', 'Update customers'),
                                                                  ('CUSTOMER_DELETE', 'CUSTOMER', 'DELETE', 'Delete customers'),

                                                                  -- Sales & Tickets
                                                                  ('OPPORTUNITY_CREATE', 'OPPORTUNITY', 'CREATE', 'Create opportunities'),
                                                                  ('OPPORTUNITY_READ', 'OPPORTUNITY', 'READ', 'View opportunities'),
                                                                  ('OPPORTUNITY_UPDATE', 'OPPORTUNITY', 'UPDATE', 'Update opportunities'),
                                                                  ('OPPORTUNITY_DELETE', 'OPPORTUNITY', 'DELETE', 'Delete opportunities'),
                                                                  ('TICKET_CREATE', 'TICKET', 'CREATE', 'Create support tickets'),
                                                                  ('TICKET_READ', 'TICKET', 'READ', 'View tickets'),
                                                                  ('TICKET_UPDATE', 'TICKET', 'UPDATE', 'Update tickets'),
                                                                  ('TICKET_DELETE', 'TICKET', 'DELETE', 'Delete tickets'),
                                                                  ('ANALYTICS_READ', 'ANALYTICS', 'READ', 'View analytics dashboard'),
                                                                  ('TENANT_MANAGE', 'TENANT', 'MANAGE', 'Manage tenant settings'),
                                                                  ('ROLE_READ', 'ROLE', 'READ', 'View roles'),
                                                                  ('ROLE_MANAGE', 'ROLE', 'MANAGE', 'Manage roles')
    ON CONFLICT (name) DO NOTHING;

-- 3. Get Role IDs
SELECT id INTO super_admin_id FROM roles WHERE name = 'ROLE_SUPER_ADMIN';
SELECT id INTO admin_id       FROM roles WHERE name = 'ROLE_ADMIN';
SELECT id INTO user_id        FROM roles WHERE name = 'ROLE_USER';
SELECT id INTO agent_id       FROM roles WHERE name = 'ROLE_AGENT';
SELECT id INTO sales_id       FROM roles WHERE name = 'ROLE_SALES';

-- 4. Assign Permissions (SUPER_ADMIN gets everything)
INSERT INTO role_permissions (role_id, permission_id)
SELECT super_admin_id, id FROM permissions
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ADMIN (Everything except TENANT_MANAGE)
INSERT INTO role_permissions (role_id, permission_id)
SELECT admin_id, id FROM permissions WHERE name != 'TENANT_MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- USER (Read only)
INSERT INTO role_permissions (role_id, permission_id)
SELECT user_id, id FROM permissions
WHERE action = 'READ' AND resource IN ('CUSTOMER', 'OPPORTUNITY', 'TICKET', 'ANALYTICS')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- AGENT (Tickets + Customers)
INSERT INTO role_permissions (role_id, permission_id)
SELECT agent_id, id FROM permissions
WHERE (resource = 'TICKET') OR (resource = 'CUSTOMER' AND action IN ('READ', 'UPDATE', 'CREATE'))
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SALES (Opportunities + Customers)
INSERT INTO role_permissions (role_id, permission_id)
SELECT sales_id, id FROM permissions
WHERE resource IN ('OPPORTUNITY', 'CUSTOMER')
    ON CONFLICT (role_id, permission_id) DO NOTHING;
END $$;