package com.windlabs.banking.transaction.controller;

import com.windlabs.banking.transaction.dto.CreateTransferRequest;
import com.windlabs.banking.transaction.dto.TransferResponse;
import com.windlabs.banking.transaction.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(
            TransferService transferService
    ) {
        this.transferService = transferService;
    }

    @PostMapping
    public TransferResponse transfer(
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @AuthenticationPrincipal
            Jwt jwt,

            @Valid
            @RequestBody
            CreateTransferRequest request
    ) {
        return transferService.transfer(
                jwt.getSubject(),
                idempotencyKey,
                request
        );
    }
}