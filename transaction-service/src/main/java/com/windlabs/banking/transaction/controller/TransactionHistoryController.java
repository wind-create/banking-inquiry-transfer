package com.windlabs.banking.transaction.controller;

import com.windlabs.banking.transaction.dto.TransactionHistoryResponse;
import com.windlabs.banking.transaction.service.TransactionHistoryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;

    public TransactionHistoryController(
            TransactionHistoryService historyService
    ) {
        this.historyService = historyService;
    }

    @GetMapping("/{accountNumber}/transactions")
    public TransactionHistoryResponse getHistory(
            @PathVariable String accountNumber,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @AuthenticationPrincipal
            Jwt jwt
    ) {

        return historyService.getHistory(
                jwt.getSubject(),
                accountNumber,
                page,
                size
        );
    }
}