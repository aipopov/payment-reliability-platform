package tech.inita.ledgerservice.posting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostingRepository extends JpaRepository<Posting, UUID> {
}
