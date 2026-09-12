ALTER TABLE account_transfers
    ADD COLUMN correlation_id VARCHAR(128);

CREATE INDEX idx_account_transfers_correlation_id
    ON account_transfers(correlation_id);