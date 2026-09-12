package com.windlabs.banking.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transfer_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_transfer_customer_idempotency",
                        columnNames = {
                                "customer_id",
                                "idempotency_key"
                        }
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferRequestEntity extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    
    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "source_account_number", nullable = false, length = 34)
    private String sourceAccountNumber;

    @Column(name = "destination_account_number", nullable = false, length = 34)
    private String destinationAccountNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "account_transfer_id")
    private UUID accountTransferId;

    @Column(
            name = "source_balance_after",
            precision = 19,
            scale = 2
    )
    private BigDecimal sourceBalanceAfter;

    @Column(
            name = "destination_balance_after",
            precision = 19,
            scale = 2
    )
    private BigDecimal destinationBalanceAfter;

    @Column(name = "processed_on")
    private Instant processedOn;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public TransferRequestEntity(
            String customerId,
            String idempotencyKey,
            String requestHash,
            String correlationId,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String currency
    ) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.correlationId = correlationId;
        this.sourceAccountNumber = sourceAccountNumber;
        this.destinationAccountNumber = destinationAccountNumber;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferStatus.PENDING;
    }

    public void markSuccess(
            UUID accountTransferId,
            BigDecimal sourceBalanceAfter,
            BigDecimal destinationBalanceAfter,
            Instant processedOn
    ) {
        this.status = TransferStatus.SUCCESS;
        this.accountTransferId = accountTransferId;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.destinationBalanceAfter = destinationBalanceAfter;
        this.processedOn = processedOn;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed(String code, String message) {
        this.status = TransferStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
    }

    public void markUnknown(String code, String message) {
        this.status = TransferStatus.UNKNOWN;
        this.failureCode = code;
        this.failureMessage = message;
    }
}