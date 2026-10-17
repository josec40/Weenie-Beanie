-- Idempotent consumers: a consumer handles each eventId at most once (marker commits with the handler's writes).
CREATE TABLE processed_event (
    consumer      VARCHAR(64)  NOT NULL,
    event_id      UUID         NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
