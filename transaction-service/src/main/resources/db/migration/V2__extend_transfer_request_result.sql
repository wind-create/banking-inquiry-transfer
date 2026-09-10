ALTER TABLE transfer_requests
    ADD COLUMN account_transfer_id UUID,
    ADD COLUMN source_balance_after NUMERIC(19, 2),
    ADD COLUMN destination_balance_after NUMERIC(19, 2),
    ADD COLUMN processed_on TIMESTAMPTZ,
    ADD COLUMN failure_message VARCHAR(500),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE transfer_requests
    DROP CONSTRAINT chk_transfer_status;

ALTER TABLE transfer_requests
    ADD CONSTRAINT chk_transfer_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'SUCCESS',
                'FAILED',
                'UNKNOWN'
            )
        );