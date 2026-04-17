-- V2__add_user_roles.sql
--
-- Multi-role user system (TENANT/OWNER/ADMIN). A user may hold multiple roles
-- simultaneously; the role is an authorization attribute, not a user type.
--
-- Conventions follow V1__baseline.sql:
--   * Per-entity sequence named <table>_seq with START WITH 1 INCREMENT BY 50
--     (Hibernate 6 default). The entity uses an explicit @SequenceGenerator
--     because Hibernate's default sequence name would be derived from the
--     entity class ("user_role_seq") rather than the table ("user_roles_seq").
--   * Timestamp column uses TIMESTAMP WITHOUT TIME ZONE to match the entity's
--     LocalDateTime field; Hibernate populates the value via @CreationTimestamp
--     so no SQL DEFAULT is declared.
--   * FK to users uses ON DELETE CASCADE so role grants disappear with the user
--     (a role grant is meaningless without its owner).
--   * The (user_id, role_name) unique constraint and the ck_role_name CHECK
--     together enforce "at most one row per (user, role) pair, role drawn from
--     the enum".

CREATE SEQUENCE user_roles_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE user_roles (
    id         BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    role_name  VARCHAR(20)  NOT NULL,
    granted_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_name),
    CONSTRAINT ck_role_name CHECK (role_name IN ('TENANT', 'OWNER', 'ADMIN'))
);

CREATE INDEX idx_user_roles_user ON user_roles (user_id);
