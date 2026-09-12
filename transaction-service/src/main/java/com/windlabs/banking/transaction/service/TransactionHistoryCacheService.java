package com.windlabs.banking.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class TransactionHistoryCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionHistoryCacheService.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public TransactionHistoryCacheService(
            StringRedisTemplate redis,
            @Value("${app.cache.transaction-history.ttl:1m}")
            Duration ttl
    ) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public Optional<String> get(
            String accountNumber,
            int page,
            int size
    ) {
        try {

            long version = getVersion(accountNumber);

            String value = redis.opsForValue().get(
                    historyKey(
                            accountNumber,
                            version,
                            page,
                            size
                    )
            );

            return Optional.ofNullable(value);

        } catch (Exception ex) {

            /*
             * Redis hanya cache.
             * Jika Redis down, fallback ke PostgreSQL.
             */
            log.warn(
                    "Unable to read transaction history cache for account {}",
                    accountNumber,
                    ex
            );

            return Optional.empty();
        }
    }

    public void put(
            String accountNumber,
            int page,
            int size,
            String value
    ) {
        try {

            long version = getVersion(accountNumber);

            redis.opsForValue().set(
                    historyKey(
                            accountNumber,
                            version,
                            page,
                            size
                    ),
                    value,
                    ttl
            );

        } catch (Exception ex) {

            log.warn(
                    "Unable to write transaction history cache for account {}",
                    accountNumber,
                    ex
            );
        }
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
             * Transfer sudah berhasil.
             * Cache failure tidak boleh rollback uang.
             */
            log.warn(
                    "Unable to invalidate transaction history cache",
                    ex
            );
        }
    }

    private long getVersion(String accountNumber) {

        String value = redis.opsForValue().get(
                versionKey(accountNumber)
        );

        if (value == null) {
            return 0;
        }

        return Long.parseLong(value);
    }

    private String versionKey(String accountNumber) {
        return "transaction-history:version:" + accountNumber;
    }

    private String historyKey(
            String accountNumber,
            long version,
            int page,
            int size
    ) {
        return String.format(
                "transaction-history:%s:v%d:p%d:s%d",
                accountNumber,
                version,
                page,
                size
        );
    }
}