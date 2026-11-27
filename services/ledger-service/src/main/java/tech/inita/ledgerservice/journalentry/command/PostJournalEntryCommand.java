package tech.inita.ledgerservice.journalentry.command;

import tech.inita.ledgerservice.journalentry.JournalEntryType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PostJournalEntryCommand(
        JournalEntryType entryType,
        String currency,
        String referenceType,
        UUID referenceId,
        String description,
        List<PostingLineCommand> postings
) {

    public PostJournalEntryCommand {
        Objects.requireNonNull(entryType, "Entry type must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        Objects.requireNonNull(postings, "Postings must not be null");

        postings = List.copyOf(postings);

        if (postings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Journal entry must contain at least one posting"
            );
        }
    }
}