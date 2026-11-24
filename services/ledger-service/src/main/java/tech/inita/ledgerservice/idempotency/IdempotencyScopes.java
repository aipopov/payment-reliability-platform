package tech.inita.ledgerservice.idempotency;

public class IdempotencyScopes {
    private IdempotencyScopes() {}

    public static final String LEDGER_POST_JOURNAL_ENTRY = "ledger:post-journal-entry";
}
