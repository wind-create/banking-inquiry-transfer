package com.windlabs.banking.account.dto;

import com.windlabs.banking.account.entity.AccountStatus;

import java.math.BigDecimal;

public record AccountInquiryResponse(
        String accountNumber,
        BigDecimal balance,
        String currency,
        AccountStatus status
) {
}