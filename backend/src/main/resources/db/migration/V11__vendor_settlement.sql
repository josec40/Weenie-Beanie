-- Vendor merchant id and on-chain vendor transfer signature.
ALTER TABLE loan
    ADD COLUMN payee_merchant_id        VARCHAR(64),
    ADD COLUMN solana_vendor_signature VARCHAR(128);
