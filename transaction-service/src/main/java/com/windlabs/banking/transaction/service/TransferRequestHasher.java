package com.windlabs.banking.transaction.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class TransferRequestHasher {

    public String hash(
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

            return HexFormat.of().formatHex(
                    digest.digest(
                            canonical.getBytes(StandardCharsets.UTF_8)
                    )
            );

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to calculate transfer hash",
                    ex
            );
        }
    }
}