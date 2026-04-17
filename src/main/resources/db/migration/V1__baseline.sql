-- V1__baseline.sql
--
-- Baseline of the schema previously managed by Hibernate ddl-auto=update.
-- Captures every @Entity in the codebase as of the merge of the properties
-- module on branch develop. From this migration forward, all schema changes
-- must be applied through Flyway and JPA runs with ddl-auto=validate.
--
-- Conventions:
--   * Postgres 16 syntax.
--   * IDs are BIGINT, populated from per-entity sequences named <table>_seq
--     with INCREMENT BY 50 (Hibernate 6 default for GenerationType.SEQUENCE
--     without an explicit @SequenceGenerator).
--   * Timestamp columns use TIMESTAMP WITHOUT TIME ZONE because the entities
--     use java.time.LocalDateTime, which Hibernate maps to TIMESTAMP. Switching
--     these columns to TIMESTAMPTZ would fail JPA validation.
--   * Indexes and unique constraints mirror the @Index / unique=true / @Column
--     annotations on the entities exactly.

-- ---------------------------------------------------------------------------
-- Sequences
-- ---------------------------------------------------------------------------

CREATE SEQUENCE users_seq              START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE contacts_seq           START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE refresh_tokens_seq     START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE verification_tokens_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE properties_seq         START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE property_media_seq     START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id              BIGINT       NOT NULL,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255),
    provider        VARCHAR(20)  NOT NULL,
    provider_id     VARCHAR(255),
    image_url       VARCHAR(500),
    email_verified  BOOLEAN      NOT NULL,
    is_active       BOOLEAN      NOT NULL,
    is_enabled      BOOLEAN      NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    last_login      TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- @Index entries on User
CREATE UNIQUE INDEX idx_user_email       ON users (email);
CREATE UNIQUE INDEX idx_user_username    ON users (username);
CREATE        INDEX idx_user_provider_id ON users (provider, provider_id);

-- ---------------------------------------------------------------------------
-- contacts
-- ---------------------------------------------------------------------------

CREATE TABLE contacts (
    id           BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    is_primary   BOOLEAN     NOT NULL,
    is_verified  BOOLEAN     NOT NULL,
    label        VARCHAR(100),
    created_at   TIMESTAMP   NOT NULL,
    updated_at   TIMESTAMP   NOT NULL,
    CONSTRAINT pk_contacts PRIMARY KEY (id),
    CONSTRAINT fk_contacts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_contact_user ON contacts (user_id);

-- ---------------------------------------------------------------------------
-- refresh_tokens
-- ---------------------------------------------------------------------------

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL,
    token       VARCHAR(500) NOT NULL,
    user_id     BIGINT       NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL,
    revoked_at  TIMESTAMP,
    device_info VARCHAR(500),
    ip_address  VARCHAR(50),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX idx_refresh_token         ON refresh_tokens (token);
CREATE        INDEX idx_refresh_token_user_id ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- verification_tokens
-- ---------------------------------------------------------------------------

CREATE TABLE verification_tokens (
    id         BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL,
    user_id    BIGINT       NOT NULL,
    token_type VARCHAR(20)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    is_used    BOOLEAN      NOT NULL,
    CONSTRAINT pk_verification_tokens PRIMARY KEY (id),
    CONSTRAINT fk_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX idx_token   ON verification_tokens (token);
CREATE        INDEX idx_user_id ON verification_tokens (user_id);

-- ---------------------------------------------------------------------------
-- properties
-- ---------------------------------------------------------------------------

CREATE TABLE properties (
    id                 BIGINT         NOT NULL,
    owner_id           BIGINT         NOT NULL,
    title              VARCHAR(200)   NOT NULL,
    description        TEXT           NOT NULL,
    property_type      VARCHAR(20)    NOT NULL,
    listing_type       VARCHAR(20)    NOT NULL,
    rental_duration    VARCHAR(20),
    furnishing_status  VARCHAR(20),
    price              NUMERIC(15, 2) NOT NULL,
    currency           VARCHAR(10),
    city               VARCHAR(100)   NOT NULL,
    district           VARCHAR(100),
    address            VARCHAR(255),
    latitude           NUMERIC(10, 7),
    longitude          NUMERIC(10, 7),
    bedrooms           INTEGER,
    bathrooms          INTEGER,
    land_size          NUMERIC(15, 2),
    land_size_unit     VARCHAR(20),
    built_area         NUMERIC(15, 2),
    year_built         INTEGER,
    features           TEXT,
    status             VARCHAR(20)    NOT NULL,
    view_count         BIGINT         NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    CONSTRAINT pk_properties PRIMARY KEY (id),
    CONSTRAINT fk_properties_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_property_owner        ON properties (owner_id);
CREATE INDEX idx_property_type         ON properties (property_type);
CREATE INDEX idx_property_listing_type ON properties (listing_type);
CREATE INDEX idx_property_status       ON properties (status);
CREATE INDEX idx_property_price        ON properties (price);
CREATE INDEX idx_property_location     ON properties (city, district);
CREATE INDEX idx_property_created      ON properties (created_at);

-- ---------------------------------------------------------------------------
-- property_media
-- ---------------------------------------------------------------------------

CREATE TABLE property_media (
    id            BIGINT       NOT NULL,
    property_id   BIGINT       NOT NULL,
    media_type    VARCHAR(20)  NOT NULL,
    url           VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    description   VARCHAR(500),
    is_primary    BOOLEAN      NOT NULL,
    display_order INTEGER      NOT NULL,
    file_size     BIGINT,
    mime_type     VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_property_media PRIMARY KEY (id),
    CONSTRAINT fk_property_media_property
        FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX idx_media_property ON property_media (property_id);
CREATE INDEX idx_media_type     ON property_media (media_type);
CREATE INDEX idx_media_order    ON property_media (property_id, display_order);
