package com.windlabs.banking.transaction.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.windlabs.banking.grpc.account.AccountGrpcServiceGrpc;
import com.windlabs.banking.grpc.account.ValidateOwnershipRequest;

import com.windlabs.banking.transaction.dto.TransactionDirection;
import com.windlabs.banking.transaction.dto.TransactionHistoryItemResponse;
import com.windlabs.banking.transaction.dto.TransactionHistoryResponse;

import com.windlabs.banking.transaction.entity.TransferRequestEntity;
import com.windlabs.banking.transaction.entity.TransferStatus;

import com.windlabs.banking.transaction.repository.TransferRequestRepository;

import io.grpc.StatusRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TransactionHistoryService {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionHistoryService.class);

    private final TransferRequestRepository repository;

    private final AccountGrpcServiceGrpc
            .AccountGrpcServiceBlockingStub accountStub;

    private final TransactionHistoryCacheService cacheService;

    private final JsonMapper jsonMapper;

    public TransactionHistoryService(
            TransferRequestRepository repository,
            AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub accountStub,
            TransactionHistoryCacheService cacheService,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.accountStub = accountStub;
        this.cacheService = cacheService;
        this.jsonMapper = jsonMapper;
    }
    @PreAuthorize(
            "hasAuthority('SCOPE_transaction.read')"
    )
    public TransactionHistoryResponse getHistory(
            String customerId,
            String accountNumber,
            int page,
            int size
    ) {

        validatePagination(page, size);

        /*
         * Ownership harus diperiksa SEBELUM Redis.
         *
         * Jangan sampai customer lain bisa mendapatkan cached data.
         */
        validateOwnership(
                customerId,
                accountNumber
        );

        var cached = cacheService.get(
                accountNumber,
                page,
                size
        );

        if (cached.isPresent()) {

            try {
            
                log.info(
                        "Transaction history cache HIT account={} page={} size={}",
                        accountNumber,
                        page,
                        size
                );
            
                return jsonMapper.readValue(
                        cached.get(),
                        TransactionHistoryResponse.class
                );
            
            } catch (JacksonException ex) {
            
                log.warn(
                        "Invalid transaction history cache for account {}",
                        accountNumber,
                        ex
                );
            }
        }

        log.info(
                "Transaction history cache MISS account={} page={} size={}",
                accountNumber,
                page,
                size
        );

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "processedOn"
                )
        );

        Page<TransferRequestEntity> result =
                repository.findHistory(
                        accountNumber,
                        TransferStatus.SUCCESS,
                        pageable
                );

        List<TransactionHistoryItemResponse> items =
                result.getContent()
                        .stream()
                        .map(entity ->
                                toResponse(
                                        accountNumber,
                                        entity
                                )
                        )
                        .toList();

        TransactionHistoryResponse response =
                new TransactionHistoryResponse(
                        accountNumber,
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        items
                );

        try {

            cacheService.put(
                    accountNumber,
                    page,
                    size,
                    jsonMapper.writeValueAsString(response)
            );
        
        } catch (JacksonException ex) {
        
            log.warn(
                    "Unable to serialize transaction history",
                    ex
            );
        }

        return response;
    }

    private void validateOwnership(
            String customerId,
            String accountNumber
    ) {

        ValidateOwnershipRequest request =
                ValidateOwnershipRequest
                        .newBuilder()
                        .setCustomerId(customerId)
                        .setAccountNumber(accountNumber)
                        .build();

        try {

            boolean valid = accountStub
                    .validateOwnership(request)
                    .getValid();

            if (!valid) {

                /*
                 * Gunakan 404 agar tidak membocorkan
                 * keberadaan rekening milik customer lain.
                 */
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                );
            }

        } catch (StatusRuntimeException ex) {

            log.error(
                    "Unable to validate account ownership",
                    ex
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Account service unavailable"
            );
        }
    }

    private TransactionHistoryItemResponse toResponse(
            String accountNumber,
            TransferRequestEntity entity
    ) {

        boolean debit =
                entity.getSourceAccountNumber()
                        .equals(accountNumber);

        return new TransactionHistoryItemResponse(
                entity.getAccountTransferId(),
                debit
                        ? TransactionDirection.DEBIT
                        : TransactionDirection.CREDIT,
                debit
                        ? entity.getDestinationAccountNumber()
                        : entity.getSourceAccountNumber(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getProcessedOn()
        );
    }

    private void validatePagination(
            int page,
            int size
    ) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }
    }
}