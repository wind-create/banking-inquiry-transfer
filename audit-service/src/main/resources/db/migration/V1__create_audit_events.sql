CREATE TABLE audit_events (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    event_key VARCHAR(300) NOT NULL UNIQUE,

    source_service VARCHAR(100) NOT NULL,

    database_name VARCHAR(100),

    schema_name VARCHAR(100),

    table_name VARCHAR(100) NOT NULL,

    operation VARCHAR(20) NOT NULL,

    record_id VARCHAR(150),

    correlation_id VARCHAR(128),

    before_data JSONB,

    after_data JSONB,

    source_ts_ms BIGINT,

    kafka_topic VARCHAR(255) NOT NULL,

    kafka_partition INTEGER NOT NULL,

    kafka_offset BIGINT NOT NULL,

    occurred_at TIMESTAMPTZ,

    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_audit_kafka_position
        UNIQUE (
            kafka_topic,
            kafka_partition,
            kafka_offset
        ),

    CONSTRAINT chk_audit_operation
        CHECK (
            operation IN (
                'CREATE',
                'UPDATE',
                'DELETE'
            )
        )
);

CREATE INDEX idx_audit_events_correlation_id
    ON audit_events(correlation_id);

CREATE INDEX idx_audit_events_table_name
    ON audit_events(table_name);

CREATE INDEX idx_audit_events_occurred_at
    ON audit_events(occurred_at DESC);