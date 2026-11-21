package tech.inita.ledgerservice.posting;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.inita.ledgerservice.account.Account;
import tech.inita.ledgerservice.journalentry.JournalEntry;
import tech.inita.ledgerservice.journalentry.JournalEntryStatus;
import tech.inita.ledgerservice.posting.exception.InvalidPostingException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "postings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(
                    name = "journal_entry_id",
                    referencedColumnName = "id",
                    nullable = false,
                    insertable = false,
                    updatable = false
            ),
            @JoinColumn(
                    name = "currency",
                    referencedColumnName = "currency",
                    nullable = false,
                    insertable = false,
                    updatable = false
            )
    })
    private JournalEntry journalEntry;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(
                    name = "account_id",
                    referencedColumnName = "id",
                    nullable = false,
                    insertable = false,
                    updatable = false
            ),
            @JoinColumn(
                    name = "currency",
                    referencedColumnName = "currency",
                    nullable = false,
                    insertable = false,
                    updatable = false
            )
    })
    private Account account;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 6)
    private PostingSide side;

    @Column(name = "amount", precision = 24, scale = 8, nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt==null) createdAt = Instant.now();
    }


    public static Posting create(
            JournalEntry journalEntry,
            Account account,
            int lineNumber,
            PostingSide side,
            BigDecimal amount
    ) {
        if (journalEntry == null) {
            throw new InvalidPostingException(
                    "Journal entry must not be null"
            );
        }

        if (account == null) {
            throw new InvalidPostingException(
                    "Account must not be null"
            );
        }

        if (journalEntry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new InvalidPostingException(
                    "Cannot add posting to a journal entry that is not in DRAFT status"
            );
        }

        if (lineNumber <= 0) {
            throw new InvalidPostingException(
                    "Posting line number must be greater than zero"
            );
        }

        if (side == null) {
            throw new InvalidPostingException(
                    "Posting side must not be null"
            );
        }

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidPostingException(
                    "Posting amount must be greater than zero"
            );
        }

        if (!journalEntry.getCurrency().equals(account.getCurrency())) {
            throw new InvalidPostingException(
                    "Journal entry currency must match account currency"
            );
        }

        Posting posting = new Posting();
        posting.journalEntry = journalEntry;
        posting.journalEntryId = journalEntry.getId();
        posting.account = account;
        posting.accountId = account.getId();
        posting.currency = journalEntry.getCurrency();
        posting.lineNumber = lineNumber;
        posting.side = side;
        posting.amount = amount;

        return posting;
    }

}
