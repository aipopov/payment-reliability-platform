package tech.inita.ledgerservice.journalentry;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.inita.ledgerservice.journalentry.exception.JournalEntryCannotBePostedException;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 40, nullable = false)
    private JournalEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private JournalEntryStatus status = JournalEntryStatus.DRAFT;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_entry_id")
    private JournalEntry reversesEntry;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt==null) createdAt = Instant.now();
    }

    private JournalEntry(
            JournalEntryType entryType,
            String currency,
            String referenceType,
            UUID referenceId,
            String description
    ) {
        this.entryType = entryType;
        this.currency = currency;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
    }

    public static JournalEntry create(
            JournalEntryType entryType,
            String currency,
            String referenceType,
            UUID referenceId,
            String description
    ) {
        return new JournalEntry(
                entryType,
                currency,
                referenceType,
                referenceId,
                description
        );
    }

    public void post() {
        if (status != JournalEntryStatus.DRAFT) {
            throw new JournalEntryCannotBePostedException(status);
        }

        this.status = JournalEntryStatus.POSTED;
        this.postedAt = Instant.now();
    }
}
