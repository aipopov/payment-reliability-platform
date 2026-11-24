package tech.inita.ledgerservice.idempotency;

public record IdempotencyResult(
        boolean newlyCreated,
        IdempotencyRecord record
) { }
