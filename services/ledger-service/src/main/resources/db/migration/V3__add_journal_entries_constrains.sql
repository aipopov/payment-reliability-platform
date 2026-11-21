alter table journal_entries
    add CONSTRAINT chk_journal_entries_type
        CHECK (entry_type IN
               ('PAYMENT_RESERVE', 'PAYMENT_CAPTURE', 'PAYMENT_RELEASE', 'PAYMENT_REFUND', 'FEE', 'ADJUSTMENT'));