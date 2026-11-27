package tech.inita.ledgerservice.journalentry.dto;

import tech.inita.ledgerservice.journalentry.JournalEntry;
import tech.inita.ledgerservice.journalentry.JournalEntryStatus;
import tech.inita.ledgerservice.journalentry.JournalEntryType;
import tech.inita.ledgerservice.posting.Posting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JournalEntryDetailResponse(
        UUID id,
        JournalEntryType entryType,
        JournalEntryStatus status,
        String currency,
        String referenceType,
        UUID referenceId,
        UUID reversesEntryId,
        String description,
        List<PostingResponse> postings,
        Instant createdAt,
        Instant postedAt
) {
    public static JournalEntryDetailResponse from(
            JournalEntry journalEntry,
            List<Posting> postings
    ) {
        return new JournalEntryDetailResponse(
                journalEntry.getId(),
                journalEntry.getEntryType(),
                journalEntry.getStatus(),
                journalEntry.getCurrency(),
                journalEntry.getReferenceType(),
                journalEntry.getReferenceId(),
                journalEntry.getReversesEntry() != null
                        ? journalEntry.getReversesEntry().getId()
                        : null,
                journalEntry.getDescription(),
                postings.stream()
                        .map(PostingResponse::from)
                        .toList(),
                journalEntry.getCreatedAt(),
                journalEntry.getPostedAt()
        );
    }
}
