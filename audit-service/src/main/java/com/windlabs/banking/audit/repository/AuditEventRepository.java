package com.windlabs.banking.audit.repository;

import com.windlabs.banking.audit.model.CdcAuditEvent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class AuditEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(
            CdcAuditEvent event
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO audit_events (
                    event_key,
                    source_service,
                    database_name,
                    schema_name,
                    table_name,
                    operation,
                    record_id,
                    correlation_id,
                    before_data,
                    after_data,
                    source_ts_ms,
                    kafka_topic,
                    kafka_partition,
                    kafka_offset,
                    occurred_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    CAST(? AS JSONB),
                    CAST(? AS JSONB),
                    ?, ?, ?, ?, ?
                )
                ON CONFLICT (event_key)
                DO NOTHING
                """,

                event.eventKey(),
                event.sourceService(),
                event.databaseName(),
                event.schemaName(),
                event.tableName(),
                event.operation(),
                event.recordId(),
                event.correlationId(),
                event.beforeData(),
                event.afterData(),
                event.sourceTsMs(),
                event.kafkaTopic(),
                event.kafkaPartition(),
                event.kafkaOffset(),

                event.occurredAt() == null
                        ? null
                        : Timestamp.from(
                                event.occurredAt()
                        )
        );
    }
}