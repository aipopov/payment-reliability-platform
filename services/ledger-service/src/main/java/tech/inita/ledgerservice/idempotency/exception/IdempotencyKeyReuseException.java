package tech.inita.ledgerservice.idempotency.exception;

public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String scope) {
        super("Idempotency key was already used with a different request: " + scope);
    }
}
