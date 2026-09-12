package com.windlabs.banking.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryItemResponse(
        UUID transactionId,
        TransactionDirection direction,
        String counterpartyAccountNumber,
        BigDecimal amount,
        String currency,
        Instant processedOn
) {
}