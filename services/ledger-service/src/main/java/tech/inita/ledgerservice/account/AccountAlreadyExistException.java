package tech.inita.ledgerservice.account;

public class AccountAlreadyExistException extends RuntimeException{
    public AccountAlreadyExistException(Exception e) {
        super("Account with the specified owner, type and currency already exists", e);
    }
}
