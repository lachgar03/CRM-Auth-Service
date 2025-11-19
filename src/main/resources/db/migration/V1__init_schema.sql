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

    CONSTRAINT chk_subdomain_format CHECK (
                                              subdomain ~* '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'
                                          ),
    CONSTRAINT chk_subscription_plan CHECK (
                                               subscription_plan IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE')
    ),
    CONSTRAINT chk_status CHECK (
                                    status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'PROVISIONING_FAILED')
    )
    );

CREATE UNIQUE INDEX idx_tenants_subdomain_lower ON tenants(LOWER(subdomain));
CREATE INDEX idx_tenants_status ON tenants(status);
COMMENT ON TABLE tenants IS 'Master tenant registry';

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

CREATE INDEX idx_roles_name ON roles(name);
COMMENT ON TABLE roles IS 'Global roles shared across all tenants';

-- Permissions Table (Global)
CREATE TABLE IF NOT EXISTS permissions (
                                           id BIGSERIAL PRIMARY KEY,
                                           name VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_permission_name CHECK (name ~ '^[A-Z_]+$'),
    CONSTRAINT chk_action CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'MANAGE'))
    );

CREATE INDEX idx_permissions_resource ON permissions(resource);
CREATE INDEX idx_permissions_action ON permissions(action);
CREATE UNIQUE INDEX idx_permissions_resource_action ON permissions(resource, action);
COMMENT ON TABLE permissions IS 'Global permissions for RBAC';

-- Role-Permission Mapping (Global)
CREATE TABLE IF NOT EXISTS role_permissions (
                                                role_id BIGINT NOT NULL,
                                                permission_id BIGINT NOT NULL,
                                                granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                                PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
    );

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

-- ============================================================
-- TENANT-SPECIFIC TABLES (WITH tenant_id)
-- ============================================================

-- Users Table (Tenant-specific)
CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,

    -- Spring Security UserDetails fields
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_non_expired BOOLEAN NOT NULL DEFAULT true,
    account_non_locked BOOLEAN NOT NULL DEFAULT true,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT true,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    -- Email must be unique per tenant
    CONSTRAINT unique_email_per_tenant UNIQUE(tenant_id, email)
    );

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
COMMENT ON TABLE users IS 'Tenant-specific user accounts';

-- User Roles Mapping (Element Collection)
CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id BIGINT NOT NULL,
                                          role_id BIGINT NOT NULL,

                                          PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    -- Note: No FK to roles table (loose coupling by design)
    );

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
COMMENT ON TABLE user_roles IS 'Mapping of tenant users to global role IDs';

-- Customers Table (Tenant-specific)
CREATE TABLE IF NOT EXISTS customers (
                                         id BIGSERIAL PRIMARY KEY,
                                         tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),

    -- Address
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),

    -- CRM info
    company_name VARCHAR(255),
    assigned_to_user_id BIGINT,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    FOREIGN KEY (assigned_to_user_id) REFERENCES users(id) ON DELETE SET NULL
    );

CREATE INDEX idx_customers_tenant_id ON customers(tenant_id);
CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_name ON customers(name);
CREATE INDEX idx_customers_assigned_user ON customers(assigned_to_user_id);
COMMENT ON TABLE customers IS 'Tenant-specific customer data';

-- Invoices Table (Tenant-specific)
CREATE TABLE IF NOT EXISTS invoices (
                                        id BIGSERIAL PRIMARY KEY,
                                        tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    customer_id BIGINT NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    amount_due NUMERIC(12, 2) NOT NULL,
    amount_paid NUMERIC(12, 2) DEFAULT 0.00,

    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'VOID', 'OVERDUE')),
    CONSTRAINT unique_invoice_number_per_tenant UNIQUE(tenant_id, invoice_number)
    );

CREATE INDEX idx_invoices_tenant_id ON invoices(tenant_id);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);
COMMENT ON TABLE invoices IS 'Tenant-specific billing invoices';

-- Orders Table (Tenant-specific)
CREATE TABLE IF NOT EXISTS orders (
                                      id BIGSERIAL PRIMARY KEY,
                                      tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    customer_id BIGINT NOT NULL,
    order_number VARCHAR(100) NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12, 2) NOT NULL,

    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_date TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT unique_order_number_per_tenant UNIQUE(tenant_id, order_number)
    );

CREATE INDEX idx_orders_tenant_id ON orders(tenant_id);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_order_date ON orders(order_date);
COMMENT ON TABLE orders IS 'Tenant-specific sales orders';

-- Tickets Table (Tenant-specific)
CREATE TABLE IF NOT EXISTS tickets (
                                       id BIGSERIAL PRIMARY KEY,
                                       tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    subject VARCHAR(255) NOT NULL,
    description TEXT,

    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(50) DEFAULT 'MEDIUM',

    customer_id BIGINT,
    agent_id BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    resolved_at TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    FOREIGN KEY (agent_id) REFERENCES users(id) ON DELETE SET NULL,

    CONSTRAINT chk_ticket_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'CLOSED')),
    CONSTRAINT chk_ticket_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
    );

CREATE INDEX idx_tickets_tenant_id ON tickets(tenant_id);
CREATE INDEX idx_tickets_customer_id ON tickets(customer_id);
CREATE INDEX idx_tickets_agent_id ON tickets(agent_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_priority ON tickets(priority);
COMMENT ON TABLE tickets IS 'Tenant-specific support tickets';

-- ============================================================
-- SEED DATA: Default Roles and Permissions
-- ============================================================

-- Insert System Roles
INSERT INTO roles (name, description, is_system_role) VALUES
                                                          ('ROLE_SUPER_ADMIN', 'Super administrator with cross-tenant access', true),
                                                          ('ROLE_ADMIN', 'Tenant administrator with full access', true),
                                                          ('ROLE_USER', 'Standard user with limited access', true),
                                                          ('ROLE_AGENT', 'Support agent role', true),
                                                          ('ROLE_SALES', 'Sales representative role', true)
    ON CONFLICT (name) DO NOTHING;

-- Insert Permissions
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

                                                                  -- Sales Management
                                                                  ('OPPORTUNITY_CREATE', 'OPPORTUNITY', 'CREATE', 'Create opportunities'),
                                                                  ('OPPORTUNITY_READ', 'OPPORTUNITY', 'READ', 'View opportunities'),
                                                                  ('OPPORTUNITY_UPDATE', 'OPPORTUNITY', 'UPDATE', 'Update opportunities'),
                                                                  ('OPPORTUNITY_DELETE', 'OPPORTUNITY', 'DELETE', 'Delete opportunities'),

                                                                  -- Ticket Management
                                                                  ('TICKET_CREATE', 'TICKET', 'CREATE', 'Create support tickets'),
                                                                  ('TICKET_READ', 'TICKET', 'READ', 'View tickets'),
                                                                  ('TICKET_UPDATE', 'TICKET', 'UPDATE', 'Update tickets'),
                                                                  ('TICKET_DELETE', 'TICKET', 'DELETE', 'Delete tickets'),

                                                                  -- Analytics
                                                                  ('ANALYTICS_READ', 'ANALYTICS', 'READ', 'View analytics dashboard'),

                                                                  -- Tenant Management
                                                                  ('TENANT_MANAGE', 'TENANT', 'MANAGE', 'Manage tenant settings'),

                                                                  -- Role Management
                                                                  ('ROLE_READ', 'ROLE', 'READ', 'View roles and permissions'),
                                                                  ('ROLE_MANAGE', 'ROLE', 'MANAGE', 'Create, update, delete roles and assign permissions'),

                                                                  -- Permission needed for user role assignment
                                                                  ('USER_ASSIGN_ROLE', 'USER', 'MANAGE', 'Assign roles to users')
    ON CONFLICT (name) DO NOTHING;

-- Assign Permissions to Roles
DO $$
DECLARE
super_admin_id BIGINT;
    admin_id BIGINT;
    user_id BIGINT;
    agent_id BIGINT;
    sales_id BIGINT;
BEGIN
    -- Get role IDs
SELECT id INTO super_admin_id FROM roles WHERE name = 'ROLE_SUPER_ADMIN';
SELECT id INTO admin_id FROM roles WHERE name = 'ROLE_ADMIN';
SELECT id INTO user_id FROM roles WHERE name = 'ROLE_USER';
SELECT id INTO agent_id FROM roles WHERE name = 'ROLE_AGENT';
SELECT id INTO sales_id FROM roles WHERE name = 'ROLE_SALES';

-- SUPER_ADMIN: All permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT super_admin_id, id FROM permissions
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ADMIN: All except TENANT_MANAGE
INSERT INTO role_permissions (role_id, permission_id)
SELECT admin_id, id FROM permissions WHERE name != 'TENANT_MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- USER: Read-only
INSERT INTO role_permissions (role_id, permission_id)
SELECT user_id, id FROM permissions
WHERE action = 'READ' AND resource IN ('CUSTOMER', 'OPPORTUNITY', 'TICKET', 'ANALYTICS')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- AGENT: Tickets (full) + Customers (read/update/create)
INSERT INTO role_permissions (role_id, permission_id)
SELECT agent_id, id FROM permissions
WHERE (resource = 'TICKET') OR (resource = 'CUSTOMER' AND action IN ('READ', 'UPDATE', 'CREATE'))
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SALES: Opportunities (full) + Customers (full)
INSERT INTO role_permissions (role_id, permission_id)
SELECT sales_id, id FROM permissions
WHERE resource IN ('OPPORTUNITY', 'CUSTOMER')
    ON CONFLICT (role_id, permission_id) DO NOTHING;
END $$;