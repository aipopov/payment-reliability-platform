package tech.inita.ledgerservice.journalentry;

public enum JournalEntryType {
    PAYMENT_RESERVE,
    PAYMENT_CAPTURE,
    PAYMENT_RELEASE,
    PAYMENT_REFUND,
    FEE,
    ADJUSTMENT
}
