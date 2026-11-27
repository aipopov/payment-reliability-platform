package tech.inita.ledgerservice.journalentry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tech.inita.ledgerservice.journalentry.JournalEntryType;
import tech.inita.ledgerservice.journalentry.command.PostJournalEntryCommand;

import java.util.List;
import java.util.UUID;

public record CreateJournalEntryRequest(

        @NotNull
        JournalEntryType entryType,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
        String currency,

        @NotNull
        @Size(max = 40)
        String referenceType,

        @NotNull
        UUID referenceId,

        @NotBlank
        @Size(max = 500)
        String description,

        @NotEmpty
        @Size(min = 2, max = 100)
        List<@NotNull @Valid PostingLineRequest> postings

) {

    public PostJournalEntryCommand toCommand() {
        return new PostJournalEntryCommand(
                entryType,
                currency,
                referenceType,
                referenceId,
                description,
                postings.stream().map(PostingLineRequest::toCommand).toList()
        );
    }
}
