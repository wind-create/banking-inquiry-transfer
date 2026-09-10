package com.windlabs.banking.transaction.repository;

import com.windlabs.banking.transaction.entity.TransferRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRequestRepository
        extends JpaRepository<TransferRequestEntity, UUID> {

    Optional<TransferRequestEntity>
    findByCustomerIdAndIdempotencyKey(
            String customerId,
            String idempotencyKey
    );
}