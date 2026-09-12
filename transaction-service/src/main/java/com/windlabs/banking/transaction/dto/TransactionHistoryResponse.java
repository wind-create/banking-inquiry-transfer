package com.windlabs.banking.transaction.dto;

import java.util.List;

public record TransactionHistoryResponse(
        String accountNumber,

        int page,
        int size,

        long totalElements,
        int totalPages,

        List<TransactionHistoryItemResponse> transactions
) {
}