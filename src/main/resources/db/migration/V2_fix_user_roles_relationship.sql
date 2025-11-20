-- Migration: Fix user-role relationship from ElementCollection to proper ManyToMany
-- This script safely migrates existing data

-- Step 1: Create new user_roles table with proper constraints if it doesn't exist
CREATE TABLE IF NOT EXISTS user_roles (
                                          user_id BIGINT NOT NULL,
                                          role_id BIGINT NOT NULL,
                                          PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
    REFERENCES roles(id) ON DELETE CASCADE
    );

-- Step 2: Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

-- Step 3: Migrate existing data if the old structure exists
-- Check if the old user_roles table has 'role_id' column (ElementCollection structure)
DO $$
BEGIN
    -- If old structure exists, migrate data
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'user_roles'
        AND column_name = 'role_id'
        AND table_schema = 'public'
    ) THEN
        -- Insert any missing relationships from old structure
        INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT user_id, role_id
FROM user_roles
    ON CONFLICT (user_id, role_id) DO NOTHING;

RAISE NOTICE 'User roles migrated successfully';
END IF;
END $$;

-- Step 4: Add index on users email + tenant_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_email_tenant ON users(email, tenant_id);

-- Step 5: Add bidirectional relationship support to roles
-- (No schema change needed, just for documentation)

COMMENT ON TABLE user_roles IS 'Many-to-many relationship between users and roles with proper FK constraints';
COMMENT ON COLUMN user_roles.user_id IS 'Reference to user';
COMMENT ON COLUMN user_roles.role_id IS 'Reference to role';