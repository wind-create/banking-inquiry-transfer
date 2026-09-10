package com.windlabs.banking.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TransactionHistoryCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TransactionHistoryCacheService.class
            );

    private final StringRedisTemplate redis;

    public TransactionHistoryCacheService(
            StringRedisTemplate redis
    ) {
        this.redis = redis;
    }

    public void invalidate(
            String sourceAccount,
            String destinationAccount
    ) {

        try {
            redis.opsForValue().increment(
                    versionKey(sourceAccount)
            );

            redis.opsForValue().increment(
                    versionKey(destinationAccount)
            );

        } catch (Exception ex) {
            /*
             * Transfer sudah SUCCESS.
             * Redis failure tidak boleh rollback transfer uang.
             */
            log.warn(
                    "Unable to invalidate transaction history cache",
                    ex
            );
        }
    }

    private String versionKey(String accountNumber) {
        return "transaction-history:version:" + accountNumber;
    }
}