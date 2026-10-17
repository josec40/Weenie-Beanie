-- Phase 8: on-time is judged against the repayment month agreed at acceptance (reschedules move repay_month).
ALTER TABLE loan ADD COLUMN original_repay_month DATE;
UPDATE loan SET original_repay_month = (repay_month - make_interval(months => reschedule_count))::date;
ALTER TABLE loan ALTER COLUMN original_repay_month SET NOT NULL;

-- Mirror of the on-chain Reputation PDA (source of truth when solana.enabled); fiat-only users have no wallet.
ALTER TABLE credential ALTER COLUMN wallet_public_key DROP NOT NULL;
ALTER TABLE credential ADD COLUMN late_repayments INTEGER NOT NULL DEFAULT 0;
ALTER TABLE credential ADD COLUMN total_repaid NUMERIC(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE credential ADD COLUMN reputation_address VARCHAR(64);
ALTER TABLE credential ADD COLUMN sbt_mint_sig VARCHAR(128);
