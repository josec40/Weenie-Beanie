-- Saga step log: one row per loan/step; on-chain steps store the signature + blockhash BEFORE submission.
CREATE TABLE settlement_step (
    loan_id       UUID         NOT NULL REFERENCES loan (id),
    step          VARCHAR(40)  NOT NULL,          -- e.g. CREATE_LOAN, WITHDRAW_FIAT#2
    status        VARCHAR(16)  NOT NULL,          -- PENDING | DONE | FAILED
    tx_signature  VARCHAR(128),
    blockhash     VARCHAR(64),
    external_id   VARCHAR(64),                    -- Nessie transfer id for fiat steps
    detail        VARCHAR(255),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (loan_id, step)
);

-- Each repayment attempt gets its own saga steps and Nessie descriptions (compensated attempts are not reused).
ALTER TABLE loan ADD COLUMN repay_attempt SMALLINT NOT NULL DEFAULT 0;
