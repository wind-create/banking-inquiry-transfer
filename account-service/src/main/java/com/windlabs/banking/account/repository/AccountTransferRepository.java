package com.windlabs.banking.account.repository;

import com.windlabs.banking.account.entity.AccountTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountTransferRepository
        extends JpaRepository<AccountTransfer, UUID> {

    Optional<AccountTransfer>
    findByCustomerIdAndIdempotencyKey(
            String customerId,
            String idempotencyKey
    );
}