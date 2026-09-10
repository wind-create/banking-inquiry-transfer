CREATE TABLE account_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transfer_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    customer_id VARCHAR(36) NOT NULL,

    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    source_account_number VARCHAR(34) NOT NULL,
    destination_account_number VARCHAR(34) NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    source_balance_after NUMERIC(19, 2) NOT NULL,
    destination_balance_after NUMERIC(19, 2) NOT NULL,

    processed_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',
    updated_by VARCHAR(150) NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT uq_account_transfer_idempotency
        UNIQUE (customer_id, idempotency_key),

    CONSTRAINT chk_account_transfer_amount
        CHECK (amount > 0),

    CONSTRAINT chk_account_transfer_different_accounts
        CHECK (source_account_number <> destination_account_number),

    CONSTRAINT fk_account_transfer_source
        FOREIGN KEY (source_account_number)
        REFERENCES accounts(account_number),

    CONSTRAINT fk_account_transfer_destination
        FOREIGN KEY (destination_account_number)
        REFERENCES accounts(account_number)
);

CREATE INDEX idx_account_transfers_source
    ON account_transfers(source_account_number, processed_on DESC);

CREATE INDEX idx_account_transfers_destination
    ON account_transfers(destination_account_number, processed_on DESC);