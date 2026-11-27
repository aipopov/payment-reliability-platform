package tech.inita.ledgerservice.journalentry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.inita.ledgerservice.account.Account;
import tech.inita.ledgerservice.account.AccountLockManager;
import tech.inita.ledgerservice.idempotency.IdempotencyResult;
import tech.inita.ledgerservice.idempotency.IdempotencyScopes;
import tech.inita.ledgerservice.idempotency.IdempotencyService;
import tech.inita.ledgerservice.idempotency.RequestHashService;
import tech.inita.ledgerservice.journalentry.command.PostJournalEntryCommand;
import tech.inita.ledgerservice.journalentry.command.PostingLineCommand;
import tech.inita.ledgerservice.journalentry.exception.JournalEntryNotFoundException;
import tech.inita.ledgerservice.journalentry.exception.UnbalancedJournalEntryException;
import tech.inita.ledgerservice.posting.Posting;
import tech.inita.ledgerservice.posting.PostingRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalEntryService {
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final AccountLockManager accountLockManager;
    private final RequestHashService requestHashService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private void validateAccountCurrencies(PostJournalEntryCommand command, List<Account> accounts) {
        for (Account account : accounts) {
            if (!account.getCurrency().equals(command.currency())) {
                throw new IllegalArgumentException(
                        "Account currency does not match journal entry currency: "
                                + account.getId()
                );
            }
        }
    }

    private void validatePostingLines(PostJournalEntryCommand command) {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;

        for (PostingLineCommand line : command.postings()) {
            if (line.side() == null || line.amount() == null
                    || line.amount().signum() <= 0) {
                throw new IllegalArgumentException("Posting side and positive amount are required");
            }

            switch (line.side()) {
                case DEBIT -> debitTotal = debitTotal.add(line.amount());
                case CREDIT -> creditTotal = creditTotal.add(line.amount());
            }
        }

        if (debitTotal.compareTo(creditTotal) != 0) throw new UnbalancedJournalEntryException(debitTotal, creditTotal);
    }

    private JournalEntry createJournalEntry(PostJournalEntryCommand command) {
        return journalEntryRepository.save(JournalEntry.create(
                command.entryType(),
                command.currency(),
                command.referenceType(),
                command.referenceId(),
                command.description()
        ));
    }

    private void applyPostings(
            PostJournalEntryCommand command,
            JournalEntry journalEntry,
            List<Account> accounts
    ) {
        List<Posting> postings = new java.util.ArrayList<>(command.postings().size());

        Map<UUID, Account> accountsById = accounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        for (int i = 0; i < command.postings().size(); i++) {
            PostingLineCommand line = command.postings().get(i);
            Account account = accountsById.get(line.accountId());
            Posting posting = Posting.create(journalEntry, account, i + 1, line.side(), line.amount());
            account.applyPosting(line.side(), line.amount());
            postings.add(posting);
        }

        postingRepository.saveAll(postings);
    }

    private List<Account> lockAccounts(PostJournalEntryCommand command) {
        List<UUID> accountIds = command.postings().stream()
                .map(PostingLineCommand::accountId)
                .toList();

        return accountLockManager.lockAccounts(accountIds);
    }

    private IdempotencyResult acquireIdempotency(PostJournalEntryCommand command, String idempotencyKey) {
        String requestHash = requestHashService.hash(command);
        return idempotencyService.acquire(IdempotencyScopes.LEDGER_POST_JOURNAL_ENTRY, idempotencyKey, requestHash);
    }

    private void completeIdempotency(
            IdempotencyResult idempotencyResult,
            JournalEntry journalEntry
    ) {
        JsonNode responsePayload = objectMapper.valueToTree(Map.of("journalEntryId", journalEntry.getId()));
        idempotencyService.complete(idempotencyResult.record(), journalEntry.getId(), responsePayload);
    }

    @Transactional
    public JournalEntry postJournalEntry(
            PostJournalEntryCommand command,
            String idempotencyKey
    ) {
        validatePostingLines(command);

        IdempotencyResult idempotencyResult = acquireIdempotency(command, idempotencyKey);
        if (!idempotencyResult.newlyCreated()) {
            UUID journalEntryId = idempotencyResult.record().getJournalEntryId();

            if (journalEntryId == null) {
                throw new IllegalStateException("Idempotency record has no associated journal entry");
            }

            return journalEntryRepository.findById(journalEntryId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Completed idempotency record references a missing journal entry"
                    ));
        }

        List<Account> accounts = lockAccounts(command);

        validateAccountCurrencies(command, accounts);

        JournalEntry journalEntry = createJournalEntry(command);

        applyPostings(command, journalEntry, accounts);

        journalEntry.post();

        completeIdempotency(idempotencyResult, journalEntry);

        return journalEntry;
    }

    @Transactional(readOnly = true)
    public JournalEntry getJournalEntry(UUID id) {
        return journalEntryRepository.findById(id).orElseThrow(() -> new JournalEntryNotFoundException(id));
    }
}