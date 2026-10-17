CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Hypertables: unique keys must include the time column.

CREATE TABLE account_transaction (
    id           VARCHAR(64)    NOT NULL,              -- Nessie _id
    occurred_at  TIMESTAMPTZ    NOT NULL,
    user_id      UUID           NOT NULL REFERENCES app_user (id),
    account_id   VARCHAR(64)    NOT NULL,
    kind         VARCHAR(16)    NOT NULL,              -- DEPOSIT | WITHDRAWAL | PURCHASE
    amount       NUMERIC(14, 2) NOT NULL,              -- signed: inflow > 0, outflow < 0
    category     VARCHAR(64)    NOT NULL,
    description  VARCHAR(255),
    merchant_id  VARCHAR(64),
    status       VARCHAR(16)    NOT NULL,
    ingested_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (id, occurred_at)
);
SELECT create_hypertable('account_transaction', by_range('occurred_at', INTERVAL '1 month'));
CREATE INDEX account_transaction_user_time_idx ON account_transaction (user_id, occurred_at DESC);

CREATE TABLE balance_snapshot (
    account_id   VARCHAR(64)    NOT NULL,
    captured_at  TIMESTAMPTZ    NOT NULL,
    user_id      UUID           NOT NULL REFERENCES app_user (id),
    balance      NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (account_id, captured_at)
);
SELECT create_hypertable('balance_snapshot', by_range('captured_at', INTERVAL '1 month'));
CREATE INDEX balance_snapshot_user_time_idx ON balance_snapshot (user_id, captured_at DESC);

CREATE TABLE audit_event (
    id           UUID         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    user_id      UUID,
    type         VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (id, occurred_at)
);
SELECT create_hypertable('audit_event', by_range('occurred_at', INTERVAL '1 month'));
CREATE INDEX audit_event_user_time_idx ON audit_event (user_id, occurred_at DESC);

-- Regular tables

CREATE TABLE recurring_expense (
    id               UUID PRIMARY KEY,
    user_id          UUID           NOT NULL REFERENCES app_user (id),
    name             VARCHAR(120)   NOT NULL,
    amount           NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    category         VARCHAR(64)    NOT NULL,
    day_of_month     SMALLINT       NOT NULL CHECK (day_of_month BETWEEN 1 AND 28),
    start_month      DATE           NOT NULL,
    duration_months  INTEGER        CHECK (duration_months > 0),   -- NULL = open-ended
    nessie_bill_id   VARCHAR(64),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX recurring_expense_user_idx ON recurring_expense (user_id);

CREATE TABLE forecast_snapshot (
    id                UUID PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES app_user (id),
    generated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    horizon_months    SMALLINT       NOT NULL,
    starting_balance  NUMERIC(14, 2) NOT NULL,
    months            JSONB          NOT NULL
);
CREATE INDEX forecast_snapshot_user_time_idx ON forecast_snapshot (user_id, generated_at DESC);

CREATE TABLE loan_offer (
    id                    UUID PRIMARY KEY,
    user_id               UUID           NOT NULL REFERENCES app_user (id),
    forecast_snapshot_id  UUID           REFERENCES forecast_snapshot (id),
    shortfall_month       DATE           NOT NULL,
    repayment_month       DATE           NOT NULL,
    principal             NUMERIC(14, 2) NOT NULL CHECK (principal > 0),
    fee                   NUMERIC(14, 2) NOT NULL CHECK (fee >= 0),
    status                VARCHAR(16)    NOT NULL,
    explanation           TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ
);
CREATE INDEX loan_offer_user_idx ON loan_offer (user_id, created_at DESC);

CREATE TABLE loan (
    id                    UUID PRIMARY KEY,
    user_id               UUID           NOT NULL REFERENCES app_user (id),
    offer_id              UUID           NOT NULL UNIQUE REFERENCES loan_offer (id),
    principal             NUMERIC(14, 2) NOT NULL CHECK (principal > 0),
    fee                   NUMERIC(14, 2) NOT NULL CHECK (fee >= 0),
    status                VARCHAR(16)    NOT NULL,
    disburse_month        DATE           NOT NULL,
    repay_month           DATE           NOT NULL,
    solana_disburse_sig   VARCHAR(128),
    solana_repay_sig      VARCHAR(128),
    nessie_deposit_id     VARCHAR(64),
    nessie_withdrawal_id  VARCHAR(64),
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX loan_user_idx ON loan (user_id, created_at DESC);

CREATE TABLE wallet (
    user_id           UUID PRIMARY KEY REFERENCES app_user (id),
    public_key        VARCHAR(64)  NOT NULL UNIQUE,
    encrypted_secret  BYTEA        NOT NULL,   -- AES-GCM; never logged
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE credential (
    user_id             UUID PRIMARY KEY REFERENCES app_user (id),
    wallet_public_key   VARCHAR(64)  NOT NULL UNIQUE,
    tier                SMALLINT     NOT NULL DEFAULT 0,
    on_time_repayments  INTEGER      NOT NULL DEFAULT 0,
    defaults            INTEGER      NOT NULL DEFAULT 0,
    sbt_mint            VARCHAR(64),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
