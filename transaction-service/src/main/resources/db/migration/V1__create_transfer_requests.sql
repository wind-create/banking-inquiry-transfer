CREATE TABLE transfer_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    customer_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    source_account_number VARCHAR(34) NOT NULL,
    destination_account_number VARCHAR(34) NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'IDR',

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_code VARCHAR(100),

    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT uq_transfer_customer_idempotency
        UNIQUE (customer_id, idempotency_key),

    CONSTRAINT chk_transfer_amount
        CHECK (amount > 0),

    CONSTRAINT chk_transfer_different_accounts
        CHECK (source_account_number <> destination_account_number),

    CONSTRAINT chk_transfer_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_transfer_requests_customer_created
    ON transfer_requests(customer_id, created_on DESC);

CREATE INDEX idx_transfer_requests_source_created
    ON transfer_requests(source_account_number, created_on DESC);

CREATE INDEX idx_transfer_requests_destination_created
    ON transfer_requests(destination_account_number, created_on DESC);