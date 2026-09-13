package com.windlabs.banking.audit.kafka;

import com.windlabs.banking.audit.model.CdcAuditEvent;
import com.windlabs.banking.audit.repository.AuditEventRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class DebeziumAuditConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DebeziumAuditConsumer.class
            );

    private final JsonMapper jsonMapper;
    private final AuditEventRepository repository;

    public DebeziumAuditConsumer(
            JsonMapper jsonMapper,
            AuditEventRepository repository
    ) {
        this.jsonMapper = jsonMapper;
        this.repository = repository;
    }

    @KafkaListener(
            topics = {
                    "banking.account.public.accounts",
                    "banking.account.public.account_transfers",
                    "banking.transaction.public.transfer_requests"
            },
            groupId = "banking-audit-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, String> record
    ) throws Exception {

        /*
         * Log paling awal.
         *
         * Dengan ini kita tahu apakah Kafka record benar-benar
         * sudah sampai ke method consumer.
         */
        log.atInfo()
                .addKeyValue(
                        "topic",
                        record.topic()
                )
                .addKeyValue(
                        "partition",
                        record.partition()
                )
                .addKeyValue(
                        "offset",
                        record.offset()
                )
                .addKeyValue(
                        "valueNull",
                        record.value() == null
                )
                .log(
                        "Kafka CDC record received"
                );

        try {

            /*
             * Debezium dapat menghasilkan tombstone record,
             * terutama pada DELETE.
             *
             * Tombstone memiliki value = null.
             */
            if (record.value() == null
                    || record.value().isBlank()) {

                log.atInfo()
                        .addKeyValue(
                                "topic",
                                record.topic()
                        )
                        .addKeyValue(
                                "partition",
                                record.partition()
                        )
                        .addKeyValue(
                                "offset",
                                record.offset()
                        )
                        .log(
                                "Skipping Kafka tombstone record"
                        );

                return;
            }

            JsonNode root =
                    jsonMapper.readTree(
                            record.value()
                    );

            if (root == null
                    || root.isNull()) {

                log.atWarn()
                        .addKeyValue(
                                "topic",
                                record.topic()
                        )
                        .addKeyValue(
                                "offset",
                                record.offset()
                        )
                        .log(
                                "Kafka record contains null JSON"
                        );

                return;
            }

            /*
             * Kafka Connect JSON Converter bisa menghasilkan:
             *
             * {
             *   "schema": {...},
             *   "payload": {...}
             * }
             *
             * Jika schemas.enable=false:
             *
             * {
             *   "before": {...},
             *   "after": {...},
             *   "source": {...},
             *   "op": "c"
             * }
             */
            JsonNode payload;

            if (root.has("payload")) {

                payload =
                        root.get("payload");

            } else {

                payload =
                        root;
            }

            /*
             * Tombstone dengan schema wrapper bisa menghasilkan
             * payload null.
             */
            if (payload == null
                    || payload.isNull()) {

                log.atInfo()
                        .addKeyValue(
                                "topic",
                                record.topic()
                        )
                        .addKeyValue(
                                "partition",
                                record.partition()
                        )
                        .addKeyValue(
                                "offset",
                                record.offset()
                        )
                        .log(
                                "Skipping Debezium event with null payload"
                        );

                return;
            }

            String op =
                    text(
                            payload,
                            "op"
                    );

            /*
             * Kalau op tidak tersedia, jangan diam.
             */
            if (op == null
                    || op.isBlank()) {

                log.atWarn()
                        .addKeyValue(
                                "topic",
                                record.topic()
                        )
                        .addKeyValue(
                                "partition",
                                record.partition()
                        )
                        .addKeyValue(
                                "offset",
                                record.offset()
                        )
                        .log(
                                "Debezium event does not contain operation"
                        );

                return;
            }

            /*
             * Snapshot initial dari Debezium.
             *
             * op = r
             *
             * Snapshot bukan perubahan bisnis baru,
             * jadi tidak dimasukkan ke audit_events.
             */
            if ("r".equals(op)) {

                log.atInfo()
                        .addKeyValue(
                                "topic",
                                record.topic()
                        )
                        .addKeyValue(
                                "partition",
                                record.partition()
                        )
                        .addKeyValue(
                                "offset",
                                record.offset()
                        )
                        .log(
                                "Skipping Debezium snapshot event"
                        );

                return;
            }

            String operation =
                    mapOperation(op);

            if (operation == null) {

                log.atWarn()
                        .addKeyValue(
                                "topic",
                                record.topic()
                        )
                        .addKeyValue(
                                "partition",
                                record.partition()
                        )
                        .addKeyValue(
                                "offset",
                                record.offset()
                        )
                        .addKeyValue(
                                "debeziumOp",
                                op
                        )
                        .log(
                                "Unsupported Debezium operation"
                        );

                return;
            }

            JsonNode source =
                    payload.get(
                            "source"
                    );

            JsonNode before =
                    payload.get(
                            "before"
                    );

            JsonNode after =
                    payload.get(
                            "after"
                    );

            String databaseName =
                    text(
                            source,
                            "db"
                    );

            String schemaName =
                    text(
                            source,
                            "schema"
                    );

            String tableName =
                    text(
                            source,
                            "table"
                    );

            /*
             * correlation_id ada pada:
             *
             * transfer_requests
             * account_transfers
             *
             * accounts sendiri tidak memiliki correlation_id.
             */
            String correlationId =
                    firstText(
                            after,
                            before,
                            "correlation_id"
                    );

            String recordId =
                    resolveRecordId(
                            after,
                            before
                    );

            /*
             * Debezium envelope mempunyai ts_ms.
             */
            Long sourceTsMs =
                    longValue(
                            payload,
                            "ts_ms"
                    );

            Instant occurredAt =
                    sourceTsMs == null
                            ? null
                            : Instant.ofEpochMilli(
                                    sourceTsMs
                            );

            /*
             * Topic + partition + offset bersifat unik.
             *
             * Cocok untuk idempotency Audit Service.
             */
            String eventKey =
                    record.topic()
                            + ":"
                            + record.partition()
                            + ":"
                            + record.offset();

            CdcAuditEvent event =
                    new CdcAuditEvent(
                            eventKey,

                            resolveService(
                                    databaseName
                            ),

                            databaseName,
                            schemaName,
                            tableName,
                            operation,
                            recordId,
                            correlationId,

                            toJson(
                                    before
                            ),

                            toJson(
                                    after
                            ),

                            sourceTsMs,

                            record.topic(),
                            record.partition(),
                            record.offset(),

                            occurredAt
                    );

            log.atInfo()
                    .addKeyValue(
                            "eventKey",
                            eventKey
                    )
                    .addKeyValue(
                            "database",
                            databaseName
                    )
                    .addKeyValue(
                            "table",
                            tableName
                    )
                    .addKeyValue(
                            "operation",
                            operation
                    )
                    .addKeyValue(
                            "correlationId",
                            correlationId
                    )
                    .log(
                            "Persisting CDC audit event"
                    );

            repository.save(
                    event
            );

            log.atInfo()
                    .addKeyValue(
                            "eventKey",
                            eventKey
                    )
                    .addKeyValue(
                            "database",
                            databaseName
                    )
                    .addKeyValue(
                            "table",
                            tableName
                    )
                    .addKeyValue(
                            "operation",
                            operation
                    )
                    .addKeyValue(
                            "correlationId",
                            correlationId
                    )
                    .log(
                            "CDC audit event persisted"
                    );

        } catch (Exception ex) {

            /*
             * Jangan swallow error.
             *
             * Kita log detail record Kafka lalu lempar ulang
             * supaya offset record gagal tidak dianggap sukses.
             */
            log.atError()
                    .addKeyValue(
                            "topic",
                            record.topic()
                    )
                    .addKeyValue(
                            "partition",
                            record.partition()
                    )
                    .addKeyValue(
                            "offset",
                            record.offset()
                    )
                    .setCause(ex)
                    .log(
                            "Failed processing CDC Kafka record"
                    );

            throw ex;
        }
    }

    private String mapOperation(
            String op
    ) {

        return switch (op) {

            case "c" ->
                    "CREATE";

            case "u" ->
                    "UPDATE";

            case "d" ->
                    "DELETE";

            default ->
                    null;
        };
    }

    private String resolveService(
            String database
    ) {

        if ("account_db".equals(database)) {

            return "account-service";
        }

        if ("transaction_db".equals(database)) {

            return "transaction-service";
        }

        return "unknown";
    }

    private String resolveRecordId(
            JsonNode after,
            JsonNode before
    ) {

        String value;

        /*
         * account_transfers
         */
        value =
                firstText(
                        after,
                        before,
                        "transfer_id"
                );

        if (value != null) {

            return value;
        }

        /*
         * transfer_requests
         */
        value =
                firstText(
                        after,
                        before,
                        "id"
                );

        if (value != null) {

            return value;
        }

        /*
         * accounts
         */
        return firstText(
                after,
                before,
                "account_number"
        );
    }

    private String firstText(
            JsonNode preferred,
            JsonNode fallback,
            String field
    ) {

        String value =
                text(
                        preferred,
                        field
                );

        if (value != null) {

            return value;
        }

        return text(
                fallback,
                field
        );
    }

    private String text(
            JsonNode node,
            String field
    ) {

        if (node == null
                || node.isNull()) {

            return null;
        }

        JsonNode value =
                node.get(
                        field
                );

        if (value == null
                || value.isNull()) {

            return null;
        }

        return value.asText();
    }

    private Long longValue(
            JsonNode node,
            String field
    ) {

        if (node == null
                || node.isNull()) {

            return null;
        }

        JsonNode value =
                node.get(
                        field
                );

        if (value == null
                || value.isNull()) {

            return null;
        }

        return value.asLong();
    }

    private String toJson(
            JsonNode node
    ) {

        if (node == null
                || node.isNull()) {

            return null;
        }

        return node.toString();
    }
}