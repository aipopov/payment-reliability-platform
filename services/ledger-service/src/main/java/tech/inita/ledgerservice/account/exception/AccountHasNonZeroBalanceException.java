package tech.inita.ledgerservice.account.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class AccountHasNonZeroBalanceException extends RuntimeException {
    public AccountHasNonZeroBalanceException(UUID id, BigDecimal balance) {
        super(String.format("Account %s has non zero balance (actual balance: %s)", id, balance));
    }
}
