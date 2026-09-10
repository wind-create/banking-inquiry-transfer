package com.windlabs.banking.account.service;

import com.windlabs.banking.account.entity.Account;
import com.windlabs.banking.account.entity.AccountStatus;
import com.windlabs.banking.account.entity.AccountTransfer;
import com.windlabs.banking.account.repository.AccountRepository;
import com.windlabs.banking.account.repository.AccountTransferRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class AccountTransferPostingService {

    private final AccountRepository accountRepository;
    private final AccountTransferRepository transferRepository;
    private final JdbcTemplate jdbcTemplate;

    public AccountTransferPostingService(
            AccountRepository accountRepository,
            AccountTransferRepository transferRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public TransferPostingResult execute(
            String customerId,
            String idempotencyKey,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal requestedAmount,
            String requestedCurrency
    ) {

        String currency = normalizeCurrency(requestedCurrency);
        BigDecimal amount = normalizeAmount(requestedAmount);

        validateRequest(
                customerId,
                idempotencyKey,
                sourceAccountNumber,
                destinationAccountNumber
        );

        String requestHash = calculateRequestHash(
                customerId,
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                currency
        );

        /*
         * PostgreSQL transaction-scoped advisory lock.
         *
         * Request dengan customer + idempotency key yang sama
         * diproses secara serial, bahkan jika datang bersamaan.
         */
        acquireIdempotencyLock(
                customerId + ":" + idempotencyKey
        );

        AccountTransfer existing = transferRepository
                .findByCustomerIdAndIdempotencyKey(
                        customerId,
                        idempotencyKey
                )
                .orElse(null);

        if (existing != null) {

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new TransferPostingException(
                        TransferPostingException.Code.IDEMPOTENCY_CONFLICT,
                        "Idempotency key has already been used for a different transfer"
                );
            }

            return toResult(existing, true);
        }

        List<Account> lockedAccounts =
                accountRepository.findAllForUpdate(
                        List.of(
                                sourceAccountNumber,
                                destinationAccountNumber
                        )
                );

        if (lockedAccounts.size() != 2) {
            throw new TransferPostingException(
                    TransferPostingException.Code.ACCOUNT_NOT_FOUND,
                    "Source or destination account not found"
            );
        }

        Account source = lockedAccounts.stream()
                .filter(a -> a.getAccountNumber()
                        .equals(sourceAccountNumber))
                .findFirst()
                .orElseThrow();

        Account destination = lockedAccounts.stream()
                .filter(a -> a.getAccountNumber()
                        .equals(destinationAccountNumber))
                .findFirst()
                .orElseThrow();

        if (!source.getCustomer()
                .getCustomerId()
                .equals(customerId)) {

            throw new TransferPostingException(
                    TransferPostingException.Code.SOURCE_ACCOUNT_NOT_OWNED,
                    "Source account does not belong to customer"
            );
        }

        if (source.getStatus() != AccountStatus.ACTIVE
                || destination.getStatus() != AccountStatus.ACTIVE) {

            throw new TransferPostingException(
                    TransferPostingException.Code.ACCOUNT_NOT_ACTIVE,
                    "Source and destination accounts must be active"
            );
        }

        if (!source.getCurrency().equalsIgnoreCase(currency)
                || !destination.getCurrency().equalsIgnoreCase(currency)) {

            throw new TransferPostingException(
                    TransferPostingException.Code.CURRENCY_MISMATCH,
                    "Account currency does not match transfer currency"
            );
        }

        if (source.getBalance().compareTo(amount) < 0) {
            throw new TransferPostingException(
                    TransferPostingException.Code.INSUFFICIENT_BALANCE,
                    "Insufficient balance"
            );
        }

        /*
         * Dua perubahan berada dalam SATU database transaction.
         */
        source.debit(amount);
        destination.credit(amount);

        AccountTransfer transfer = new AccountTransfer(
                customerId,
                idempotencyKey,
                requestHash,
                sourceAccountNumber,
                destinationAccountNumber,
                amount,
                currency,
                source.getBalance(),
                destination.getBalance()
        );

        transferRepository.save(transfer);

        /*
         * Hibernate akan melakukan UPDATE accounts +
         * INSERT account_transfers pada commit transaction yang sama.
         */

        return toResult(transfer, false);
    }

    private void acquireIdempotencyLock(String lockKey) {

        jdbcTemplate.execute(
                (ConnectionCallback<Void>) connection -> {

                    try (var statement = connection.prepareStatement(
                            """
                            SELECT pg_advisory_xact_lock(
                                hashtextextended(?, 0)
                            )
                            """
                    )) {

                        statement.setString(1, lockKey);
                        statement.execute();
                    }

                    return null;
                }
        );
    }

    private void validateRequest(
            String customerId,
            String idempotencyKey,
            String sourceAccountNumber,
            String destinationAccountNumber
    ) {

        if (customerId == null || customerId.isBlank()) {
            invalid("Customer id is required");
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {

            invalid("Valid idempotency key is required");
        }

        if (sourceAccountNumber == null
                || sourceAccountNumber.isBlank()) {
            invalid("Source account is required");
        }

        if (destinationAccountNumber == null
                || destinationAccountNumber.isBlank()) {
            invalid("Destination account is required");
        }

        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            invalid(
                    "Source and destination accounts must be different"
            );
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            invalid("Transfer amount must be greater than zero");
        }

        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            invalid(
                    "Transfer amount supports at most 2 decimal places"
            );

            throw ex;
        }
    }

    private String normalizeCurrency(String currency) {

        if (currency == null || currency.isBlank()) {
            invalid("Currency is required");
        }

        String normalized = currency
                .trim()
                .toUpperCase();

        if (normalized.length() != 3) {
            invalid("Currency must contain 3 characters");
        }

        return normalized;
    }

    private String calculateRequestHash(
            String customerId,
            String source,
            String destination,
            BigDecimal amount,
            String currency
    ) {

        try {

            String canonical = String.join(
                    "|",
                    customerId,
                    source,
                    destination,
                    amount.toPlainString(),
                    currency
            );

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to calculate transfer request hash",
                    ex
            );
        }
    }

    private TransferPostingResult toResult(
            AccountTransfer transfer,
            boolean replayed
    ) {

        return new TransferPostingResult(
                transfer.getTransferId(),
                transfer.getSourceAccountNumber(),
                transfer.getDestinationAccountNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getSourceBalanceAfter(),
                transfer.getDestinationBalanceAfter(),
                transfer.getProcessedOn(),
                replayed
        );
    }

    private void invalid(String message) {
        throw new TransferPostingException(
                TransferPostingException.Code.INVALID_REQUEST,
                message
        );
    }
}