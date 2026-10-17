-- In-app notifications; id = the source event's id, so a redelivered event never creates a duplicate.
CREATE TABLE notification (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user (id),
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(160) NOT NULL,
    body        VARCHAR(500) NOT NULL,
    link        VARCHAR(80),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX notification_user_time_idx ON notification (user_id, created_at DESC);
