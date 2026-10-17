CREATE TABLE app_user (
    id                          UUID PRIMARY KEY,
    email                       VARCHAR(320) NOT NULL UNIQUE,
    password_hash               VARCHAR(255) NOT NULL,
    nessie_customer_id          VARCHAR(64),
    nessie_checking_account_id  VARCHAR(64),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
