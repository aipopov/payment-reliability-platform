package tech.inita.ledgerservice.account.exception;

import java.util.UUID;

public class AccountNotOpenException extends RuntimeException {

    public AccountNotOpenException(UUID accountId) {
        super("Account is not open: " + accountId);
    }
}