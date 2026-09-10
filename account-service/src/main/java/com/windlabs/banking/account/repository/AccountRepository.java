package com.windlabs.banking.account.repository;

import com.windlabs.banking.account.entity.Account;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomer_CustomerId(String customerId);

    boolean existsByAccountNumberAndCustomer_CustomerId(
            String accountNumber,
            String customerId
    );

    Optional<Account> findByAccountNumberAndCustomer_CustomerId(
            String accountNumber,
            String customerId
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT a
           FROM Account a
           JOIN FETCH a.customer
           WHERE a.accountNumber IN :accountNumbers
           ORDER BY a.accountNumber
           """)
    List<Account> findAllForUpdate(
            @Param("accountNumbers")
            Collection<String> accountNumbers
    );
}