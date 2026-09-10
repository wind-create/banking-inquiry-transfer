package com.windlabs.banking.account.service;

public class TransferPostingException extends RuntimeException {

    private final Code code;

    public TransferPostingException(
            Code code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        ACCOUNT_NOT_FOUND,
        SOURCE_ACCOUNT_NOT_OWNED,
        ACCOUNT_NOT_ACTIVE,
        CURRENCY_MISMATCH,
        INSUFFICIENT_BALANCE,
        IDEMPOTENCY_CONFLICT
    }
}