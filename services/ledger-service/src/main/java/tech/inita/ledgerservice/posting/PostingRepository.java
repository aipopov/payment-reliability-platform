package tech.inita.ledgerservice.posting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostingRepository extends JpaRepository<Posting, UUID> {
    List<Posting> findAllByJournalEntryId(UUID journalEntryId);
    List<Posting> findAllByJournalEntry_ReferenceId(UUID referenceId);
}
