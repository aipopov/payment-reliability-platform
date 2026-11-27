package tech.inita.ledgerservice.journalentry.dto;

import tech.inita.ledgerservice.posting.Posting;
import tech.inita.ledgerservice.posting.PostingSide;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingResponse(
        UUID accountId,
        PostingSide side,
        BigDecimal amount
) {
    public static PostingResponse from(Posting posting) {
        return new PostingResponse(
                posting.getAccountId(),
                posting.getSide(),
                posting.getAmount()
        );
    }
}
