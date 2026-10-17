-- Simulated month clock for the demo (single row). scenario_anchor = real month the demo history was seeded for.
CREATE TABLE demo_clock (
    id               SMALLINT PRIMARY KEY CHECK (id = 1),
    month_offset     INTEGER     NOT NULL DEFAULT 0,
    scenario_anchor  DATE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO demo_clock (id, month_offset, scenario_anchor) VALUES (1, 0, date_trunc('month', now())::date);

ALTER TABLE loan_offer
    ADD COLUMN deficit            NUMERIC(14, 2),
    ADD COLUMN projected_surplus  NUMERIC(14, 2),
    ADD COLUMN tier               SMALLINT,
    ADD COLUMN decided_at         TIMESTAMPTZ;
CREATE UNIQUE INDEX loan_offer_one_open_per_user ON loan_offer (user_id) WHERE status = 'OFFERED';

ALTER TABLE loan
    ADD COLUMN grace_until_month  DATE,
    ADD COLUMN reschedule_count   SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN repaid_at          TIMESTAMPTZ,
    ADD COLUMN last_failure       VARCHAR(64);
CREATE UNIQUE INDEX loan_one_open_per_user ON loan (user_id) WHERE status NOT IN ('REPAID', 'DEFAULTED');
