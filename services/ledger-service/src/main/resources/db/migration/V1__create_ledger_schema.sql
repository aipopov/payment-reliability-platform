-- 1. Financial accounts
CREATE TABLE accounts (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          owner_type VARCHAR(20) NOT NULL,
                          owner_id UUID NOT NULL,

                          account_type VARCHAR(40) NOT NULL,
                          account_class VARCHAR(20) NOT NULL,
                          normal_side VARCHAR(6) NOT NULL,

                          currency VARCHAR(3) NOT NULL,

                          balance NUMERIC(24, 8) NOT NULL DEFAULT 0,
                          allow_negative BOOLEAN NOT NULL DEFAULT FALSE,

                          status VARCHAR(10) NOT NULL DEFAULT 'OPEN',

                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT uq_accounts_owner_type_currency
                              UNIQUE (owner_type, owner_id, account_type, currency),

                          CONSTRAINT uq_accounts_id_currency
                              UNIQUE (id, currency),

                          CONSTRAINT chk_accounts_owner_type
                              CHECK (owner_type IN ('CUSTOMER', 'PLATFORM', 'PROVIDER')),

                          CONSTRAINT chk_accounts_class
                              CHECK (account_class IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),

                          CONSTRAINT chk_accounts_normal_side
                              CHECK (
                                  (account_class IN ('ASSET', 'EXPENSE') AND normal_side = 'DEBIT')
                                      OR
                                  (account_class IN ('LIABILITY', 'EQUITY', 'REVENUE') AND normal_side = 'CREDIT')
                                  ),

                          CONSTRAINT chk_accounts_currency
                              CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_accounts_balance
        CHECK (allow_negative OR balance >= 0),

    CONSTRAINT chk_accounts_status
        CHECK (status IN ('OPEN', 'CLOSED'))
);


-- 2. Journal entries
CREATE TABLE journal_entries (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                 entry_type VARCHAR(40) NOT NULL,
                                 status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',

                                 currency VARCHAR(3) NOT NULL,

                                 reference_type VARCHAR(40),
                                 reference_id UUID,

                                 reverses_entry_id UUID REFERENCES journal_entries(id),

                                 description VARCHAR(500),

                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 posted_at TIMESTAMPTZ,

                                 CONSTRAINT uq_journal_entries_id_currency
                                     UNIQUE (id, currency),

                                 CONSTRAINT uq_journal_entries_reversal
                                     UNIQUE (reverses_entry_id),

                                 CONSTRAINT chk_journal_entries_status
                                     CHECK (status IN ('DRAFT', 'POSTED')),

                                 CONSTRAINT chk_journal_entries_currency
                                     CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_journal_entries_posted_at
        CHECK (
            (status = 'DRAFT' AND posted_at IS NULL)
            OR
            (status = 'POSTED' AND posted_at IS NOT NULL)
        )
);


-- 3. Financial postings
CREATE TABLE postings (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                          journal_entry_id UUID NOT NULL,
                          account_id UUID NOT NULL,

                          currency VARCHAR(3) NOT NULL,

                          line_number INTEGER NOT NULL,
                          side VARCHAR(6) NOT NULL,

                          amount NUMERIC(24, 8) NOT NULL,

                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_postings_journal_entry
                              FOREIGN KEY (journal_entry_id, currency)
                                  REFERENCES journal_entries(id, currency),

                          CONSTRAINT fk_postings_account
                              FOREIGN KEY (account_id, currency)
                                  REFERENCES accounts(id, currency),

                          CONSTRAINT uq_postings_entry_line
                              UNIQUE (journal_entry_id, line_number),

                          CONSTRAINT chk_postings_side
                              CHECK (side IN ('DEBIT', 'CREDIT')),

                          CONSTRAINT chk_postings_amount
                              CHECK (amount > 0),

                          CONSTRAINT chk_postings_line_number
                              CHECK (line_number > 0)
);


-- 4. Idempotency records
CREATE TABLE idempotency_records (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                     scope VARCHAR(100) NOT NULL,
                                     idempotency_key VARCHAR(255) NOT NULL,

                                     request_hash CHAR(64) NOT NULL,

                                     status VARCHAR(12) NOT NULL DEFAULT 'PROCESSING',

                                     journal_entry_id UUID REFERENCES journal_entries(id),

                                     response_payload JSONB,

                                     created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     completed_at TIMESTAMPTZ,

                                     CONSTRAINT uq_idempotency_scope_key
                                         UNIQUE (scope, idempotency_key),

                                     CONSTRAINT chk_idempotency_status
                                         CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

CREATE INDEX idx_accounts_owner
    ON accounts(owner_type, owner_id);

CREATE INDEX idx_journal_entries_reference
    ON journal_entries(reference_type, reference_id);

CREATE INDEX idx_journal_entries_created_at
    ON journal_entries(created_at);

CREATE INDEX idx_postings_account
    ON postings(account_id);

CREATE INDEX idx_postings_journal_entry
    ON postings(journal_entry_id);

CREATE INDEX idx_idempotency_journal_entry
    ON idempotency_records(journal_entry_id);
