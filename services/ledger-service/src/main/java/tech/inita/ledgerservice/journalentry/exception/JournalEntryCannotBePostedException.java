package tech.inita.ledgerservice.journalentry.exception;

import tech.inita.ledgerservice.journalentry.JournalEntryStatus;

public class JournalEntryCannotBePostedException extends RuntimeException {

    public JournalEntryCannotBePostedException(JournalEntryStatus status) {
        super("Cannot post journal entry with status: " + status);
    }
}
