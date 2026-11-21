package tech.inita.ledgerservice.account.exception;

import java.util.UUID;

public class InsufficientAccountBalanceException extends RuntimeException {

    public InsufficientAccountBalanceException(UUID accountId) {
        super("Insufficient balance on account: " + accountId);
    }
}
