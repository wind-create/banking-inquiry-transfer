ALTER TABLE transfer_requests
    ADD COLUMN correlation_id VARCHAR(128);

CREATE INDEX idx_transfer_requests_correlation_id
    ON transfer_requests(correlation_id);