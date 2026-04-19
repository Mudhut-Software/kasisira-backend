-- V6__add_owner_orgs.sql
-- Org aggregate: an owner_org is the authorization boundary for listings,
-- tenancies, fault tickets, and subscriptions. A user can own multiple orgs
-- and hold multiple memberships (creator gets an OWNER membership with all
-- permissions true; invited managers get MANAGER with per-flag permissions).

CREATE SEQUENCE owner_org_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE membership_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE invite_seq     START WITH 1 INCREMENT BY 50;

CREATE TABLE owner_org (
    id                BIGINT                   NOT NULL,
    creator_user_id   BIGINT                   NOT NULL,
    name              VARCHAR(120)             NOT NULL,
    verified_at       TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_owner_org            PRIMARY KEY (id),
    CONSTRAINT fk_owner_org_creator    FOREIGN KEY (creator_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_owner_org_creator ON owner_org(creator_user_id);

CREATE TABLE membership (
    id             BIGINT                   NOT NULL,
    owner_org_id   BIGINT                   NOT NULL,
    user_id        BIGINT                   NOT NULL,
    role           VARCHAR(20)              NOT NULL,
    permissions    JSONB                    NOT NULL,
    invited_by     BIGINT,
    accepted_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_membership              PRIMARY KEY (id),
    CONSTRAINT fk_membership_org          FOREIGN KEY (owner_org_id) REFERENCES owner_org(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_user         FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_invited_by   FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_membership_user_org     UNIQUE (owner_org_id, user_id),
    CONSTRAINT ck_membership_role         CHECK (role IN ('OWNER', 'MANAGER'))
);

CREATE INDEX idx_membership_user ON membership(user_id);
CREATE INDEX idx_membership_org  ON membership(owner_org_id);

CREATE TABLE invite (
    id             BIGINT                   NOT NULL,
    owner_org_id   BIGINT                   NOT NULL,
    email          VARCHAR(150),
    phone_number   VARCHAR(20),
    role           VARCHAR(20)              NOT NULL,
    permissions    JSONB                    NOT NULL,
    token_hash     VARCHAR(255)             NOT NULL,
    invited_by     BIGINT                   NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at    TIMESTAMP WITH TIME ZONE,
    revoked_at     TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_invite             PRIMARY KEY (id),
    CONSTRAINT fk_invite_org         FOREIGN KEY (owner_org_id) REFERENCES owner_org(id) ON DELETE CASCADE,
    CONSTRAINT fk_invite_inviter     FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_invite_role        CHECK (role IN ('OWNER', 'MANAGER')),
    CONSTRAINT ck_invite_recipient   CHECK (email IS NOT NULL OR phone_number IS NOT NULL)
);

CREATE UNIQUE INDEX idx_invite_token_hash ON invite(token_hash);
CREATE INDEX idx_invite_org ON invite(owner_org_id);
CREATE INDEX idx_invite_open ON invite(owner_org_id, accepted_at, revoked_at) WHERE accepted_at IS NULL AND revoked_at IS NULL;
