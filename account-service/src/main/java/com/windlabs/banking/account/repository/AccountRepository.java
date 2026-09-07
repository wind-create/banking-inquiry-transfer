package com.windlabs.banking.account.repository;

import com.windlabs.banking.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}