package tech.inita.ledgerservice.journalentry.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import tech.inita.ledgerservice.journalentry.command.PostingLineCommand;
import tech.inita.ledgerservice.posting.PostingSide;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingLineRequest(

        @NotNull
        UUID accountId,

        @NotNull
        PostingSide side,

        @NotNull
        @DecimalMin(value = "0.00000001", message = "Amount must be greater than zero")
        @Digits(
                integer = 16,
                fraction = 8,
                message = "Amount must have at most 16 integer digits and 8 fractional digits"
        )
        BigDecimal amount
) {
    public PostingLineCommand toCommand() {
        return new PostingLineCommand(
                accountId,
                side,
                amount
        );
    }
}
