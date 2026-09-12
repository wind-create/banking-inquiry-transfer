package com.windlabs.banking.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "account_transfers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_account_transfer_idempotency",
                        columnNames = {
                                "customer_id",
                                "idempotency_key"
                        }
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountTransfer extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "transfer_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID transferId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 128
    )
    private String idempotencyKey;

    @Column(
            name = "correlation_id",
            length = 128
    )
    private String correlationId;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Column(
            name = "source_account_number",
            nullable = false,
            length = 34
    )
    private String sourceAccountNumber;

    @Column(
            name = "destination_account_number",
            nullable = false,
            length = 34
    )
    private String destinationAccountNumber;

    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(
            name = "source_balance_after",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal sourceBalanceAfter;

    @Column(
            name = "destination_balance_after",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal destinationBalanceAfter;

    @Column(name = "processed_on", nullable = false)
    private Instant processedOn;

    public AccountTransfer(
            String customerId,
            String idempotencyKey,
            String requestHash,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String currency,
            BigDecimal sourceBalanceAfter,
            BigDecimal destinationBalanceAfter,
            String correlationId
    ) {
        this.transferId = UUID.randomUUID();
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
        this.requestHash = requestHash;
        this.sourceAccountNumber = sourceAccountNumber;
        this.destinationAccountNumber = destinationAccountNumber;
        this.amount = amount;
        this.currency = currency;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.destinationBalanceAfter = destinationBalanceAfter;
        this.processedOn = Instant.now();
    }
}