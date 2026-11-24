package tech.inita.ledgerservice.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scope", length = 100, nullable = false)
    private String scope;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private JsonNode responsePayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt==null) createdAt = Instant.now();
    }

    public static IdempotencyRecord create(
            String scope,
            String idempotencyKey,
            String requestHash
    ) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.scope = Objects.requireNonNull(scope);
        record.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        record.requestHash = Objects.requireNonNull(requestHash);
        return record;
    }

    public void complete(UUID journalEntryId, JsonNode responsePayload) {
        if (this.journalEntryId != null
                || this.responsePayload != null
                || this.completedAt != null) {
            throw new IllegalStateException(
                    "Idempotency record is already completed"
            );
        }
        this.journalEntryId = Objects.requireNonNull(journalEntryId);
        this.responsePayload = Objects.requireNonNull(responsePayload);
        this.completedAt = Instant.now();
    }

}
