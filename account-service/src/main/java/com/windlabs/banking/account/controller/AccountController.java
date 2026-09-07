package com.windlabs.banking.account.controller;

import com.windlabs.banking.account.dto.AccountInquiryResponse;
import com.windlabs.banking.account.dto.CustomerAccountsResponse;
import com.windlabs.banking.account.service.AccountInquiryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountInquiryService accountInquiryService;

    public AccountController(AccountInquiryService accountInquiryService) {
        this.accountInquiryService = accountInquiryService;
    }

    @GetMapping("/customers/{customerId}/accounts")
    public CustomerAccountsResponse getCustomerAccounts(
            @PathVariable String customerId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountInquiryService.getCustomerAccounts(
                customerId,
                jwt.getSubject()
        );
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    public AccountInquiryResponse getBalance(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return accountInquiryService.getBalance(
                accountNumber,
                jwt.getSubject()
        );
    }
}