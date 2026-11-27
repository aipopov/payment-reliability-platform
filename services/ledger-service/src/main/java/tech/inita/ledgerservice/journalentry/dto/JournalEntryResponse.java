package tech.inita.ledgerservice.journalentry.dto;

import tech.inita.ledgerservice.journalentry.JournalEntry;
import tech.inita.ledgerservice.journalentry.JournalEntryStatus;
import tech.inita.ledgerservice.journalentry.JournalEntryType;

import java.time.Instant;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        JournalEntryType entryType,
        JournalEntryStatus status,
        String currency,
        String referenceType,
        UUID referenceId,
        UUID reversesEntryId,
        String description,
        Instant createdAt,
        Instant postedAt
) {
    public static JournalEntryResponse from(JournalEntry journalEntry) {
        return new JournalEntryResponse(
                journalEntry.getId(),
                journalEntry.getEntryType(),
                journalEntry.getStatus(),
                journalEntry.getCurrency(),
                journalEntry.getReferenceType(),
                journalEntry.getReferenceId(),
                journalEntry.getReversesEntry() != null? journalEntry.getReversesEntry().getId(): null,
                journalEntry.getDescription(),
                journalEntry.getCreatedAt(),
                journalEntry.getPostedAt()
        );
    }
}
