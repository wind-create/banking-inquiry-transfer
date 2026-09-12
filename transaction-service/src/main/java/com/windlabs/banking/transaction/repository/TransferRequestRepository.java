package com.windlabs.banking.transaction.repository;

import com.windlabs.banking.transaction.entity.TransferRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import com.windlabs.banking.transaction.entity.TransferStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransferRequestRepository
        extends JpaRepository<TransferRequestEntity, UUID> {

    Optional<TransferRequestEntity>
    findByCustomerIdAndIdempotencyKey(
            String customerId,
            String idempotencyKey
    );


    @Query("""
            SELECT t
            FROM TransferRequestEntity t
            WHERE t.status = :status
              AND (
                    t.sourceAccountNumber = :accountNumber
                    OR
                    t.destinationAccountNumber = :accountNumber
                  )
            """)
    Page<TransferRequestEntity> findHistory(
            @Param("accountNumber") String accountNumber,
            @Param("status") TransferStatus status,
            Pageable pageable
    );
}