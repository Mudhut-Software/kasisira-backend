-- V4__add_otp_challenges.sql
-- OTP challenges for phone-number-based authentication.
-- Stored with a bcrypt hash of the code; single-use via consumed_at; 10-minute
-- default expiry enforced by the service; up to 3 requests per phone per hour
-- rate-limited in OtpService (Task 11). CHECK constraint mirrors OtpPurpose enum.

CREATE SEQUENCE otp_challenges_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE otp_challenges (
    id           BIGINT       NOT NULL,
    phone_number VARCHAR(20)  NOT NULL,
    code_hash    VARCHAR(255) NOT NULL,
    purpose      VARCHAR(20)  NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    consumed_at  TIMESTAMP,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_otp_challenges PRIMARY KEY (id),
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('LOGIN', 'SIGNUP'))
);

CREATE INDEX idx_otp_phone_purpose_created ON otp_challenges(phone_number, purpose, created_at DESC);
CREATE INDEX idx_otp_phone_active ON otp_challenges(phone_number, expires_at) WHERE consumed_at IS NULL;
