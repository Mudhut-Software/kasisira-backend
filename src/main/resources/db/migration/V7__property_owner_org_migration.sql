-- V7__property_owner_org_migration.sql
-- Introduces owner_org ownership of properties.
-- For every distinct properties.owner_id, create a default owner_org named
-- "<username>'s Workspace", add an OWNER membership with full permissions,
-- grant the global OWNER role (idempotently), and rewrite properties to
-- point at the new org. Then drop the old owner_id column.

ALTER TABLE properties ADD COLUMN owner_org_id BIGINT;

DO $$
DECLARE
    rec RECORD;
    new_org_id BIGINT;
    full_perms JSONB := '{"manage_listings": true, "manage_tenancies": true, "manage_faults": true, "manage_team": true, "manage_billing": true}'::jsonb;
BEGIN
    FOR rec IN SELECT u.id AS user_id, u.username
               FROM users u
               WHERE EXISTS (SELECT 1 FROM properties p WHERE p.owner_id = u.id)
    LOOP
        -- Create the org
        INSERT INTO owner_org (id, creator_user_id, name, created_at, updated_at)
        VALUES (nextval('owner_org_seq'), rec.user_id, rec.username || '''s Workspace', now(), now())
        RETURNING id INTO new_org_id;

        -- OWNER membership with full permissions
        INSERT INTO membership (id, owner_org_id, user_id, role, permissions, accepted_at, created_at, updated_at)
        VALUES (nextval('membership_seq'), new_org_id, rec.user_id, 'OWNER', full_perms, now(), now(), now());

        -- Grant global OWNER role (idempotent -- user_roles has uk_user_role)
        INSERT INTO user_roles (id, user_id, role_name, granted_at)
        VALUES (nextval('user_roles_seq'), rec.user_id, 'OWNER', now())
        ON CONFLICT (user_id, role_name) DO NOTHING;

        -- Point their properties at the new org
        UPDATE properties SET owner_org_id = new_org_id WHERE owner_id = rec.user_id;
    END LOOP;
END $$;

-- Enforce NOT NULL and add the new FK
ALTER TABLE properties ALTER COLUMN owner_org_id SET NOT NULL;
ALTER TABLE properties
    ADD CONSTRAINT fk_properties_owner_org FOREIGN KEY (owner_org_id) REFERENCES owner_org(id) ON DELETE RESTRICT;

-- Drop the old FK + index + column
ALTER TABLE properties DROP CONSTRAINT fk_properties_owner;
DROP INDEX IF EXISTS idx_property_owner;
ALTER TABLE properties DROP COLUMN owner_id;

CREATE INDEX idx_property_owner_org ON properties(owner_org_id);
