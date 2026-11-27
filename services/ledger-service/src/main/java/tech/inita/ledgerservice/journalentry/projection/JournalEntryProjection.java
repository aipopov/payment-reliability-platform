package tech.inita.ledgerservice.journalentry.projection;

import tech.inita.ledgerservice.journalentry.JournalEntryStatus;
import tech.inita.ledgerservice.journalentry.JournalEntryType;

import java.time.Instant;
import java.util.UUID;

public record JournalEntryProjection(
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
) {}
