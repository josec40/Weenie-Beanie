-- Purpose and purchase metadata on loan offers and loans.
ALTER TABLE loan_offer
    ADD COLUMN purpose          VARCHAR(32)    NOT NULL DEFAULT 'BASELINE',
    ADD COLUMN purchase_label   VARCHAR(80),
    ADD COLUMN purchase_amount  NUMERIC(14, 2);

ALTER TABLE loan
    ADD COLUMN purpose          VARCHAR(32)    NOT NULL DEFAULT 'BASELINE',
    ADD COLUMN purchase_label   VARCHAR(80),
    ADD COLUMN purchase_amount  NUMERIC(14, 2);
