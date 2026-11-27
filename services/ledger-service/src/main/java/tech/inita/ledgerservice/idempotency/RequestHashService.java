package tech.inita.ledgerservice.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.inita.ledgerservice.journalentry.JournalEntryType;
import tech.inita.ledgerservice.journalentry.command.PostJournalEntryCommand;
import tech.inita.ledgerservice.posting.PostingSide;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.security.MessageDigest;

import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestHashService {

    private final ObjectMapper objectMapper;

    public String hash(PostJournalEntryCommand command) {
        try {
            CanonicalPostJournalEntry canonicalCommand = new CanonicalPostJournalEntry(
                    command.entryType(),
                    command.currency(),
                    command.referenceType(),
                    command.referenceId(),
                    command.description(),
                    command.postings().stream()
                            .map(posting ->
                                    new CanonicalPostingLine(
                                            posting.accountId(),
                                            posting.side(),
                                            posting.amount()
                                                    .stripTrailingZeros()
                                                    .toPlainString()
                                    )
                            )
                            .toList()
            );

            byte[] payload = objectMapper.writeValueAsBytes(canonicalCommand);

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload);

            return HexFormat.of().formatHex(digest);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to calculate request hash", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private record CanonicalPostJournalEntry(
            JournalEntryType entryType,
            String currency,
            String referenceType,
            UUID referenceId,
            String description,
            List<CanonicalPostingLine> postings
    ) {
    }

    private record CanonicalPostingLine(
            UUID accountId,
            PostingSide side,
            String amount
    ) {
    }
}
