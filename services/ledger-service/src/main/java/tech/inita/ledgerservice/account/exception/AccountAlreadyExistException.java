package tech.inita.ledgerservice.account.exception;

public class AccountAlreadyExistException extends RuntimeException{
    public AccountAlreadyExistException(Exception e) {
        super("Account with the specified owner, type and currency already exists", e);
    }
}
