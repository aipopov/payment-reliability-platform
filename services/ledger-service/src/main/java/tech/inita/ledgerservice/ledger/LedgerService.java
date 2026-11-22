package tech.inita.ledgerservice.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.inita.ledgerservice.account.Account;
import tech.inita.ledgerservice.account.AccountLockManager;
import tech.inita.ledgerservice.journalentry.JournalEntry;
import tech.inita.ledgerservice.journalentry.JournalEntryRepository;
import tech.inita.ledgerservice.journalentry.exception.UnbalancedJournalEntryException;
import tech.inita.ledgerservice.ledger.command.PostJournalEntryCommand;
import tech.inita.ledgerservice.ledger.command.PostingLineCommand;
import tech.inita.ledgerservice.posting.Posting;
import tech.inita.ledgerservice.posting.PostingRepository;
import tech.inita.ledgerservice.posting.PostingSide;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerService {
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final AccountLockManager accountLockManager;

    @Transactional
    public UUID postJournalEntry(PostJournalEntryCommand command) {

        List<UUID> accountIds = command.postings().stream()
                .map(PostingLineCommand::accountId)
                .toList();

        List<Account> accounts = accountLockManager.lockAccounts(accountIds);

        Map<UUID, Account> accountsById = accounts.stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        for (Account account : accounts) {
            if (!account.getCurrency().equals(command.currency())) {
                throw new IllegalArgumentException(
                        "Account currency does not match journal entry currency: "
                                + account.getId()
                );
            }
        }

        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;

        for (PostingLineCommand line : command.postings()) {
            if (line.side() == null || line.amount() == null
                    || line.amount().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Posting side and positive amount are required"
                );
            }

            if (line.side() == PostingSide.DEBIT) {
                debitTotal = debitTotal.add(line.amount());
            } else {
                creditTotal = creditTotal.add(line.amount());
            }
        }

        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new UnbalancedJournalEntryException(
                    debitTotal,
                    creditTotal
            );
        }

        JournalEntry journalEntry = JournalEntry.create(
                command.entryType(),
                command.currency(),
                command.referenceType(),
                command.referenceId(),
                command.description()
        );

        journalEntry = journalEntryRepository.save(journalEntry);

        List<Posting> postings = new java.util.ArrayList<>(
                command.postings().size()
        );

        for (int i = 0; i < command.postings().size(); i++) {
            PostingLineCommand line = command.postings().get(i);

            Account account = accountsById.get(line.accountId());

            Posting posting = Posting.create(
                    journalEntry,
                    account,
                    i + 1,
                    line.side(),
                    line.amount()
            );

            account.applyPosting(line.side(), line.amount());

            postings.add(posting);
        }

        postingRepository.saveAll(postings);

        journalEntry.post();

        return journalEntry.getId();
    }
}
