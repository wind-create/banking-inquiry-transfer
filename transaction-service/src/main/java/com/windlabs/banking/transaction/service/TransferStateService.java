package com.windlabs.banking.transaction.service;

import com.windlabs.banking.transaction.dto.CreateTransferRequest;
import com.windlabs.banking.transaction.dto.TransferResponse;
import com.windlabs.banking.transaction.entity.TransferRequestEntity;
import com.windlabs.banking.transaction.entity.TransferStatus;
import com.windlabs.banking.transaction.repository.TransferRequestRepository;
import com.windlabs.banking.grpc.account.ExecuteTransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferStateService {

    private final TransferRequestRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public TransferStateService(
            TransferRequestRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PreparedTransfer prepare(
            String customerId,
            String idempotencyKey,
            String requestHash,
            CreateTransferRequest request
    ) {

        acquireLock(customerId + ":" + idempotencyKey);

        Optional<TransferRequestEntity> existing =
                repository.findByCustomerIdAndIdempotencyKey(
                        customerId,
                        idempotencyKey
                );

        if (existing.isPresent()) {

            TransferRequestEntity entity = existing.get();

            if (!entity.getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key has already been used for another request"
                );
            }

            if (entity.getStatus() == TransferStatus.SUCCESS) {
                return new PreparedTransfer(
                        entity.getId(),
                        toResponse(entity, true)
                );
            }

            if (entity.getStatus() == TransferStatus.FAILED) {
                throw new ResponseStatusException(
                        mapFailureStatus(entity.getFailureCode()),
                        entity.getFailureMessage()
                );
            }

            /*
             * PENDING / UNKNOWN / PROCESSING:
             * boleh dipanggil kembali ke Account Service dengan
             * Idempotency-Key yang sama.
             */
            return new PreparedTransfer(entity.getId(), null);
        }

        TransferRequestEntity entity =
                new TransferRequestEntity(
                        customerId,
                        idempotencyKey,
                        requestHash,
                        request.sourceAccountNumber(),
                        request.destinationAccountNumber(),
                        request.amount(),
                        request.currency()
                );

        repository.saveAndFlush(entity);

        return new PreparedTransfer(entity.getId(), null);
    }

    @Transactional
    public TransferResponse markSuccess(
            UUID requestId,
            ExecuteTransferResponse response
    ) {

        TransferRequestEntity entity = repository
                .findById(requestId)
                .orElseThrow();

        entity.markSuccess(
                UUID.fromString(response.getTransferId()),
                new java.math.BigDecimal(
                        response.getSourceBalanceAfter()
                ),
                new java.math.BigDecimal(
                        response.getDestinationBalanceAfter()
                ),
                Instant.parse(response.getProcessedOn())
        );

        return toResponse(
                entity,
                response.getReplayed()
        );
    }

    @Transactional
    public void markFailed(
            UUID requestId,
            String code,
            String message
    ) {
        repository.findById(requestId)
                .orElseThrow()
                .markFailed(code, message);
    }

    @Transactional
    public void markUnknown(
            UUID requestId,
            String code,
            String message
    ) {
        repository.findById(requestId)
                .orElseThrow()
                .markUnknown(code, message);
    }

    private void acquireLock(String key) {

        jdbcTemplate.execute(
                (ConnectionCallback<Void>) connection -> {

                    try (var ps = connection.prepareStatement(
                            """
                            SELECT pg_advisory_xact_lock(
                                hashtextextended(?, 0)
                            )
                            """
                    )) {
                        ps.setString(1, key);
                        ps.execute();
                    }

                    return null;
                }
        );
    }

    private TransferResponse toResponse(
            TransferRequestEntity entity,
            boolean replayed
    ) {
        return new TransferResponse(
                entity.getAccountTransferId(),
                entity.getStatus(),
                entity.getSourceAccountNumber(),
                entity.getDestinationAccountNumber(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getSourceBalanceAfter(),
                entity.getDestinationBalanceAfter(),
                entity.getProcessedOn(),
                replayed
        );
    }

    private HttpStatus mapFailureStatus(String code) {

        if ("PERMISSION_DENIED".equals(code)) {
            return HttpStatus.FORBIDDEN;
        }

        if ("NOT_FOUND".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }

        if ("ALREADY_EXISTS".equals(code)) {
            return HttpStatus.CONFLICT;
        }

        return HttpStatus.BAD_REQUEST;
    }

    public record PreparedTransfer(
            UUID requestId,
            TransferResponse completedResponse
    ) {
    }
}