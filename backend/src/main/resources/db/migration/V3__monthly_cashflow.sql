-- WITH NO DATA lets this run inside Flyway's transaction; NessieSyncService refreshes after ingest.
CREATE MATERIALIZED VIEW monthly_cashflow
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT user_id,
       time_bucket(INTERVAL '1 month', occurred_at)            AS month,
       category,
       sum(CASE WHEN amount > 0 THEN amount ELSE 0 END)        AS income,
       sum(CASE WHEN amount < 0 THEN -amount ELSE 0 END)       AS spend,
       sum(amount)                                             AS net,
       count(*)                                                AS txn_count
FROM account_transaction
GROUP BY user_id, month, category
WITH NO DATA;

SELECT add_continuous_aggregate_policy('monthly_cashflow',
       start_offset      => INTERVAL '3 years',
       end_offset        => INTERVAL '1 day',
       schedule_interval => INTERVAL '1 hour');
