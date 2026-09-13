package com.windlabs.banking.audit.model;

import java.time.Instant;

public record CdcAuditEvent(

        String eventKey,

        String sourceService,

        String databaseName,

        String schemaName,

        String tableName,

        String operation,

        String recordId,

        String correlationId,

        String beforeData,

        String afterData,

        Long sourceTsMs,

        String kafkaTopic,

        int kafkaPartition,

        long kafkaOffset,

        Instant occurredAt
) {
}