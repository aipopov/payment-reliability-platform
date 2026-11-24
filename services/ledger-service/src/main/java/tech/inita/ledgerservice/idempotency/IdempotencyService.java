package tech.inita.ledgerservice.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tech.inita.ledgerservice.idempotency.exception.IdempotencyKeyReuseException;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyRecordRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyResult acquire(String scope, String idempotencyKey, String requestHash) {

        int inserted = repository.tryCreate(scope, idempotencyKey, requestHash);

        IdempotencyRecord record = repository.findByScopeAndIdempotencyKey(scope, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record was not found after registration"));

        if (inserted == 1) return new IdempotencyResult(true, record);

        if (!record.getRequestHash().equals(requestHash)) throw new IdempotencyKeyReuseException(scope);

        if (record.getJournalEntryId() == null
                || record.getResponsePayload() == null
                || record.getCompletedAt() == null)
            throw new IllegalStateException("Existing idempotency record has no completed result");

        return new IdempotencyResult(false, record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(
            IdempotencyRecord record,
            UUID journalEntryId,
            JsonNode responsePayload
    ) {
        record.complete(journalEntryId, responsePayload);
    }
}
