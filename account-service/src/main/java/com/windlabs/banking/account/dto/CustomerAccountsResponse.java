package com.windlabs.banking.account.dto;

import java.util.List;

public record CustomerAccountsResponse(
        String customerId,
        List<AccountInquiryResponse> accounts
) {
}