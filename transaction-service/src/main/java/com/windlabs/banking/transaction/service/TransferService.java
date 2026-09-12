package com.windlabs.banking.transaction.service;

import com.windlabs.banking.grpc.account.AccountGrpcServiceGrpc;
import com.windlabs.banking.grpc.account.ExecuteTransferRequest;
import com.windlabs.banking.grpc.account.ExecuteTransferResponse;
import com.windlabs.banking.transaction.dto.CreateTransferRequest;
import com.windlabs.banking.transaction.dto.TransferResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class TransferService {

    private static final Logger log =
        LoggerFactory.getLogger(
                TransferService.class
        );

    private final AccountGrpcServiceGrpc
            .AccountGrpcServiceBlockingStub accountStub;

    private final TransferStateService stateService;
    private final TransferRequestHasher hasher;
    private final TransactionHistoryCacheService cacheService;

    private final Duration deadline;

    public TransferService(
            AccountGrpcServiceGrpc
                    .AccountGrpcServiceBlockingStub accountStub,
            TransferStateService stateService,
            TransferRequestHasher hasher,
            TransactionHistoryCacheService cacheService,
            @Value("${app.grpc.account.deadline:3s}")
            Duration deadline
    ) {
        this.accountStub = accountStub;
        this.stateService = stateService;
        this.hasher = hasher;
        this.cacheService = cacheService;
        this.deadline = deadline;
    }

    @PreAuthorize("hasAuthority('SCOPE_transfer.write')")
    public TransferResponse transfer(
            String customerId,
            String idempotencyKey,
            CreateTransferRequest rawRequest
    ) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid Idempotency-Key header is required"
            );
        }

        if (rawRequest.sourceAccountNumber()
                .equals(rawRequest.destinationAccountNumber())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source and destination account must be different"
            );
        }

        BigDecimal amount;

        try {
            amount = rawRequest.amount()
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount supports at most 2 decimal places"
            );
        }

        String currency = rawRequest.currency()
                .trim()
                .toUpperCase();

        CreateTransferRequest request =
                new CreateTransferRequest(
                        rawRequest.sourceAccountNumber().trim(),
                        rawRequest.destinationAccountNumber().trim(),
                        amount,
                        currency
                );

        String requestHash = hasher.hash(
                customerId,
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amount(),
                request.currency()
        );

        TransferStateService.PreparedTransfer prepared =
                stateService.prepare(
                        customerId,
                        idempotencyKey,
                        requestHash,
                        request
                );

        /*
         * Request yang sama sudah SUCCESS sebelumnya.
         */
        if (prepared.completedResponse() != null) {
            return prepared.completedResponse();
        }

        log.atInfo()
            .addKeyValue(
                    "customerId",
                    customerId
            )
            .addKeyValue(
                    "idempotencyKey",
                    idempotencyKey
            )
            .log(
                    "Calling account service for transfer"
            );

        ExecuteTransferRequest grpcRequest =
                ExecuteTransferRequest.newBuilder()
                        .setCustomerId(customerId)
                        .setIdempotencyKey(idempotencyKey)
                        .setSourceAccountNumber(
                                request.sourceAccountNumber()
                        )
                        .setDestinationAccountNumber(
                                request.destinationAccountNumber()
                        )
                        .setAmount(
                                request.amount().toPlainString()
                        )
                        .setCurrency(request.currency())
                        .build();

        try {

            ExecuteTransferResponse grpcResponse =
                    accountStub
                            .withDeadlineAfter(
                                    deadline.toMillis(),
                                    TimeUnit.MILLISECONDS
                            )
                            .executeTransfer(grpcRequest);

            TransferResponse response =
                    stateService.markSuccess(
                            prepared.requestId(),
                            grpcResponse
                    );
                
            cacheService.invalidate(
                    request.sourceAccountNumber(),
                    request.destinationAccountNumber()
            );

            log.atInfo()
                    .addKeyValue(
                            "customerId",
                            customerId
                    )
                    .addKeyValue(
                            "transferId",
                            response.transferId()
                    )
                    .addKeyValue(
                            "status",
                            response.status()
                    )
                    .addKeyValue(
                            "replayed",
                            response.replayed()
                    )
                    .log(
                            "Transfer completed successfully"
                    );
                
            return response;

        } catch (StatusRuntimeException ex) {

            Status.Code code = ex.getStatus().getCode();

            String message =
                    ex.getStatus().getDescription() == null
                            ? code.name()
                            : ex.getStatus().getDescription();

            if (isDeterministicFailure(code)) {

                stateService.markFailed(
                        prepared.requestId(),
                        code.name(),
                        message
                );

                log.atWarn()
                    .addKeyValue(
                            "customerId",
                            customerId
                    )
                    .addKeyValue(
                            "idempotencyKey",
                            idempotencyKey
                    )
                    .addKeyValue(
                            "grpcStatus",
                            code.name()
                    )
                    .log(
                            "Transfer failed"
                    );

                throw mapBusinessException(code, message);
            }

            /*
             * Tidak tahu apakah Account Service sempat commit.
             *
             * Jangan tandai FAILED.
             */
            stateService.markUnknown(
                    prepared.requestId(),
                    code.name(),
                    message
            );

            log.atError()
                .addKeyValue(
                        "customerId",
                        customerId
                )
                .addKeyValue(
                        "idempotencyKey",
                        idempotencyKey
                )
                .addKeyValue(
                        "grpcStatus",
                        code.name()
                )
                .log(
                        "Transfer outcome unknown"
                );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Transfer outcome is unknown. Retry using the same Idempotency-Key."
            );
        }
    }

    private boolean isDeterministicFailure(
            Status.Code code
    ) {
        return switch (code) {
            case INVALID_ARGUMENT,
                 NOT_FOUND,
                 PERMISSION_DENIED,
                 FAILED_PRECONDITION,
                 ALREADY_EXISTS -> true;

            default -> false;
        };
    }

    private ResponseStatusException mapBusinessException(
            Status.Code code,
            String message
    ) {

        return switch (code) {

            case NOT_FOUND ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            message
                    );

            case PERMISSION_DENIED ->
                    new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            message
                    );

            case ALREADY_EXISTS ->
                    new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            message
                    );

            case FAILED_PRECONDITION,
                 INVALID_ARGUMENT ->
                    new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            message
                    );

            default ->
                    new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unexpected transfer error"
                    );
        };
    }
}