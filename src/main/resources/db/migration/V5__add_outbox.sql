-- V5__add_outbox.sql
-- Notifications outbox. Domain writes enqueue here in the same transaction as the
-- business write; a scheduled worker (Task 14) drains PENDING rows in
-- notBefore-order with exponential backoff on failure.
-- Payload is JSONB so channel-specific shape (SMS body, email template+vars) is
-- kept at the worker/dispatcher boundary rather than leaking cross-module types.

CREATE SEQUENCE outbox_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE outbox (
    id          BIGINT        NOT NULL,
    channel     VARCHAR(20)   NOT NULL,
    recipient   VARCHAR(255)  NOT NULL,
    payload     JSONB         NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts    INTEGER       NOT NULL DEFAULT 0,
    not_before  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error  TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    CONSTRAINT ck_outbox_channel CHECK (channel IN ('SMS', 'EMAIL', 'INAPP')),
    CONSTRAINT ck_outbox_status  CHECK (status  IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_due ON outbox(not_before) WHERE status = 'PENDING';
