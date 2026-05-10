ALTER TABLE notification_fanout_outbox
    ADD COLUMN requeue_count INT NOT NULL DEFAULT 0;

ALTER TABLE notification_fanout_outbox
    ADD COLUMN last_requeued_at TIMESTAMPTZ;

ALTER TABLE notification_fanout_outbox
    ADD COLUMN last_requeued_by VARCHAR(255);

ALTER TABLE notification_fanout_outbox
    ADD COLUMN dead_at TIMESTAMPTZ;

CREATE TABLE notification_dead_letter_requeue_requests (
    id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    dead_letter_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    requested_by_user_id VARCHAR(128) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dead_letter_requeue_idem UNIQUE (source, dead_letter_id, idempotency_key)
);

CREATE INDEX idx_dead_letter_requeue_dead_id ON notification_dead_letter_requeue_requests (dead_letter_id);
