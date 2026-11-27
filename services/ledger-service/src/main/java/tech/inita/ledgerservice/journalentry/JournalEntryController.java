package tech.inita.ledgerservice.journalentry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.inita.ledgerservice.journalentry.dto.CreateJournalEntryRequest;
import tech.inita.ledgerservice.journalentry.dto.JournalEntryResponse;

@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final JournalEntryService journalEntryService;

    @PostMapping
    public ResponseEntity<JournalEntryResponse> postJournalEntry(
            @Valid @RequestBody CreateJournalEntryRequest request,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank String idempotencyKey
    ) {
        JournalEntry journalEntry = journalEntryService.postJournalEntry(request.toCommand(), idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JournalEntryResponse.from(journalEntry));
    }
}
