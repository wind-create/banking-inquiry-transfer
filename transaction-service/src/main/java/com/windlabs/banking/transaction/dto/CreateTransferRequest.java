package com.windlabs.banking.transaction.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateTransferRequest(

        @NotBlank
        @Size(max = 34)
        String sourceAccountNumber,

        @NotBlank
        @Size(max = 34)
        String destinationAccountNumber,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency
) {
}