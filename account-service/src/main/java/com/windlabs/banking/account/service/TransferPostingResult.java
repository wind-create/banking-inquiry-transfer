package com.windlabs.banking.account.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferPostingResult(
        UUID transferId,
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