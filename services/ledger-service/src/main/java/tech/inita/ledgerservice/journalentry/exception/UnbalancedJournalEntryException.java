package tech.inita.ledgerservice.journalentry.exception;

import java.math.BigDecimal;

public class UnbalancedJournalEntryException extends RuntimeException {

    public UnbalancedJournalEntryException(
            BigDecimal debitTotal,
            BigDecimal creditTotal
    ) {
        super(
                "Journal entry is not balanced. Debit total: "
                        + debitTotal
                        + ", credit total: "
                        + creditTotal
        );
    }
}