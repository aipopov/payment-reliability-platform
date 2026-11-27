package tech.inita.ledgerservice.journalentry.exception;

import java.util.UUID;

public class JournalEntryNotFoundException extends RuntimeException {

    public JournalEntryNotFoundException(
            UUID journalEntryId
    ) {
        super("Journal entry not found:. Debit total: " + journalEntryId);
    }
}