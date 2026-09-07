package com.windlabs.banking.account.service;

import com.windlabs.banking.account.dto.AccountInquiryResponse;
import com.windlabs.banking.account.dto.CustomerAccountsResponse;
import com.windlabs.banking.account.entity.Account;
import com.windlabs.banking.account.repository.AccountRepository;
import com.windlabs.banking.account.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountInquiryService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public AccountInquiryService(
            CustomerRepository customerRepository,
            AccountRepository accountRepository
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    @PreAuthorize("hasAuthority('SCOPE_account.read')")
    public CustomerAccountsResponse getCustomerAccounts(
            String requestedCustomerId,
            String authenticatedCustomerId
    ) {
        requireCustomerIdentity(authenticatedCustomerId);

        if (!authenticatedCustomerId.equals(requestedCustomerId)) {
            throw new AccessDeniedException(
                    "You are not allowed to access this customer"
            );
        }

        if (!customerRepository.existsByCustomerId(requestedCustomerId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        List<AccountInquiryResponse> accounts = accountRepository
                .findByCustomer_CustomerId(requestedCustomerId)
                .stream()
                .map(this::toResponse)
                .toList();

        return new CustomerAccountsResponse(
                requestedCustomerId,
                accounts
        );
    }

    @PreAuthorize("hasAuthority('SCOPE_account.read')")
    public AccountInquiryResponse getBalance(
            String accountNumber,
            String authenticatedCustomerId
    ) {
        requireCustomerIdentity(authenticatedCustomerId);

        Account account = accountRepository
                .findByAccountNumberAndCustomer_CustomerId(
                        accountNumber,
                        authenticatedCustomerId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        return toResponse(account);
    }

    private void requireCustomerIdentity(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new AccessDeniedException(
                    "Customer identity is required"
            );
        }
    }

    private AccountInquiryResponse toResponse(Account account) {
        return new AccountInquiryResponse(
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus()
        );
    }
}