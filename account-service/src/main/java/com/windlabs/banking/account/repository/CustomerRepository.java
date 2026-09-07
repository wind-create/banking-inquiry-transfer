package com.windlabs.banking.account.repository;

import com.windlabs.banking.account.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);
}