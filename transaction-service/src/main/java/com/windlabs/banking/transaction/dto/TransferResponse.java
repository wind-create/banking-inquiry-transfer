package com.windlabs.banking.transaction.dto;

import com.windlabs.banking.transaction.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        TransferStatus status,

        String sourceAccountNumber,
        String destinationAccountNumber,

        BigDecimal amount,
        String currency,

        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter,

        Instant processedOn,

        boolean replayed
) {
}