package tech.inita.ledgerservice.journalentry.command;

import tech.inita.ledgerservice.posting.PostingSide;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingLineCommand(
        UUID accountId,
        PostingSide side,
        BigDecimal amount
) {}
