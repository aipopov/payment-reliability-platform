package tech.inita.ledgerservice.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (
                scope,
                idempotency_key,
                request_hash
            )
            VALUES (
                :scope,
                :idempotencyKey,
                :requestHash
            )
            ON CONFLICT (scope, idempotency_key)
            DO NOTHING
            """, nativeQuery = true)
    int tryCreate(
            @Param("scope") String scope,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );

    Optional<IdempotencyRecord> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);
}