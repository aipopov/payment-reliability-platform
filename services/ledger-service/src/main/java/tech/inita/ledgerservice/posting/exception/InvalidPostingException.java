package tech.inita.ledgerservice.posting.exception;

public class InvalidPostingException extends RuntimeException {

    public InvalidPostingException(String message) {
        super(message);
    }
}
