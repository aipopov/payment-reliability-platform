package tech.inita.ledgerservice.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.inita.ledgerservice.AbstractIntegrationTest;
import tech.inita.ledgerservice.account.*;
import tech.inita.ledgerservice.account.exception.AccountNotOpenException;
import tech.inita.ledgerservice.account.exception.InsufficientAccountBalanceException;
import tech.inita.ledgerservice.journalentry.JournalEntry;
import tech.inita.ledgerservice.journalentry.JournalEntryRepository;
import tech.inita.ledgerservice.journalentry.JournalEntryStatus;
import tech.inita.ledgerservice.journalentry.JournalEntryType;
import tech.inita.ledgerservice.journalentry.exception.UnbalancedJournalEntryException;
import tech.inita.ledgerservice.ledger.command.PostJournalEntryCommand;
import tech.inita.ledgerservice.ledger.command.PostingLineCommand;
import tech.inita.ledgerservice.posting.Posting;
import tech.inita.ledgerservice.posting.PostingRepository;
import tech.inita.ledgerservice.posting.PostingSide;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
public class LedgerServiceIntegrationTest extends AbstractIntegrationTest{
    @Autowired
    LedgerService ledgerService;

    @Autowired
    AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PostingRepository postingRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private static final String CURRENCY = "RUB";

    private static final BigDecimal INITIAL_AMOUNT = BigDecimal.valueOf(1000);
    private static final BigDecimal RESERVE_AMOUNT = BigDecimal.valueOf(500);

    private Account createAccount(OwnerType ownerType, UUID ownerId, AccountType accountType) {
        return accountService.createAccount(
                ownerType,
                ownerId,
                accountType,
                CURRENCY,
                false
        );
    }

    private void assertAccountBalance(UUID accountId, BigDecimal expected) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow();

        assertThat(account.getBalance()).isEqualByComparingTo(expected);
    }

    private void assertPosting(Posting posting, UUID accountId, PostingSide side, BigDecimal amount) {
        assertThat(posting.getAccountId()).isEqualTo(accountId);
        assertThat(posting.getSide()).isEqualTo(side);
        assertThat(posting.getAmount()).isEqualByComparingTo(amount);
    }

    private void assertJournalEntry(JournalEntry journalEntry, UUID depositEntryId, JournalEntryType type) {
        assertThat(journalEntry.getId()).isEqualTo(depositEntryId);
        assertThat(journalEntry.getEntryType()).isEqualTo(type);
        assertThat(journalEntry.getStatus()).isEqualTo(JournalEntryStatus.POSTED);
        assertThat(journalEntry.getCurrency()).isEqualTo(CURRENCY);
        assertThat(journalEntry.getPostedAt()).isNotNull();
    }

    private record TestAccounts(
            UUID customerId,
            UUID platformId,
            UUID referenceId,
            Account cash,
            Account available,
            Account reserved
    ) {
    }

    private TestAccounts createTestAccounts() {
        UUID customerId = UUID.randomUUID();
        UUID platformId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();

        Account cashAccount = createAccount(OwnerType.PLATFORM, platformId, AccountType.CASH);
        Account availableAccount = createAccount(OwnerType.CUSTOMER, customerId, AccountType.AVAILABLE);
        Account reservedAccount = createAccount(OwnerType.CUSTOMER, customerId, AccountType.RESERVED);

        return new TestAccounts(
                customerId,
                platformId,
                referenceId,
                cashAccount,
                availableAccount,
                reservedAccount
        );
    }

    @Test
    void postJournalEntrySuccessfulCheck() {

        TestAccounts accounts = createTestAccounts();

        UUID depositEntryId = ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                "INITIAL_DEPOSIT",
                UUID.randomUUID(),
                "Initial deposit of 1000 RUB",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, INITIAL_AMOUNT),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, INITIAL_AMOUNT)
                )

        ), UUID.randomUUID().toString());


        assertAccountBalance(accounts.cash().getId(), INITIAL_AMOUNT);
        assertAccountBalance(accounts.available().getId(), INITIAL_AMOUNT);
        assertAccountBalance(accounts.reserved().getId(), BigDecimal.ZERO);


        UUID reserveEntryId = ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.PAYMENT_RESERVE,
                CURRENCY,
                "PAYMENT",
                UUID.randomUUID(),
                "Reserve 500 RUB",
                List.of(
                        new PostingLineCommand(accounts.available().getId(), PostingSide.DEBIT, RESERVE_AMOUNT),
                        new PostingLineCommand(accounts.reserved().getId(), PostingSide.CREDIT, RESERVE_AMOUNT)
                )

        ), UUID.randomUUID().toString());

        assertAccountBalance(accounts.cash().getId(), INITIAL_AMOUNT);
        assertAccountBalance(accounts.available().getId(), INITIAL_AMOUNT.subtract(RESERVE_AMOUNT));
        assertAccountBalance(accounts.reserved().getId(), RESERVE_AMOUNT);

        assertThat(journalEntryRepository.findById(depositEntryId)).isPresent();
        assertThat(journalEntryRepository.findById(reserveEntryId)).isPresent();

        List<Posting> depositPostings = postingRepository.findAllByJournalEntryId(depositEntryId);
        List<Posting> reservePostings = postingRepository.findAllByJournalEntryId(reserveEntryId);

        assertThat(depositPostings).hasSize(2);
        assertThat(reservePostings).hasSize(2);

        assertThat(depositPostings).anySatisfy(posting ->
                assertPosting(posting, accounts.cash().getId(), PostingSide.DEBIT, INITIAL_AMOUNT)
        );

        assertThat(depositPostings).anySatisfy(posting ->
                assertPosting(posting, accounts.available().getId(), PostingSide.CREDIT, INITIAL_AMOUNT)
        );

        assertThat(reservePostings).anySatisfy(posting ->
                assertPosting(posting, accounts.available().getId(), PostingSide.DEBIT, RESERVE_AMOUNT)
        );

        assertThat(reservePostings).anySatisfy(posting ->
                assertPosting(posting, accounts.reserved().getId(), PostingSide.CREDIT, RESERVE_AMOUNT)
        );

        JournalEntry depositEntry = journalEntryRepository.findById(depositEntryId).orElseThrow();

        JournalEntry reserveEntry = journalEntryRepository.findById(reserveEntryId).orElseThrow();

        assertJournalEntry(depositEntry, depositEntryId, JournalEntryType.ADJUSTMENT);
        assertJournalEntry(reserveEntry, reserveEntryId, JournalEntryType.PAYMENT_RESERVE);
    }

    @Test
    void checkUnbalancedJournalEntry() {

        TestAccounts accounts = createTestAccounts();
        String referenceType = "INITIAL_DEPOSIT";

        assertThrows(UnbalancedJournalEntryException.class, () -> ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                referenceType,
                accounts.referenceId(),
                "Initial deposit of 1000 RUB",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, INITIAL_AMOUNT),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, INITIAL_AMOUNT.subtract(new BigDecimal(100)))
                )

        ), UUID.randomUUID().toString()));

        assertThat(journalEntryRepository.findByReferenceTypeAndReferenceId(referenceType, accounts.referenceId())).isEmpty();
        assertThat(postingRepository.findAllByJournalEntry_ReferenceId(accounts.referenceId())).isEmpty();

        assertAccountBalance(accounts.cash().getId(), BigDecimal.ZERO);
        assertAccountBalance(accounts.available().getId(), BigDecimal.ZERO);
    }

    @Test
    void checkInsufficientAccountBalance() {

        TestAccounts accounts = createTestAccounts();
        String referenceType = "PAYMENT";

        assertThrows(InsufficientAccountBalanceException.class, () -> ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.PAYMENT_RESERVE,
                CURRENCY,
                referenceType,
                accounts.referenceId(),
                "Reserve 2000 RUB",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, BigDecimal.valueOf(1000)),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.DEBIT, BigDecimal.valueOf(1000)),
                        new PostingLineCommand(accounts.reserved().getId(), PostingSide.CREDIT, BigDecimal.valueOf(2000))
                )

        ), UUID.randomUUID().toString()));

        assertThat(journalEntryRepository.findByReferenceTypeAndReferenceId(referenceType, accounts.referenceId())).isEmpty();
        assertThat(postingRepository.findAllByJournalEntry_ReferenceId(accounts.referenceId())).isEmpty();

        assertAccountBalance(accounts.cash().getId(), BigDecimal.ZERO);
        assertAccountBalance(accounts.available().getId(), BigDecimal.ZERO);
        assertAccountBalance(accounts.reserved().getId(), BigDecimal.ZERO);
    }

    @Test
    void checkConcurrentReserve() throws Exception {

        TestAccounts accounts = createTestAccounts();
        BigDecimal initialAmount = BigDecimal.valueOf(1000);
        BigDecimal reserveAmount = BigDecimal.valueOf(1000);

        ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                "INITIAL_DEPOSIT",
                UUID.randomUUID(),
                "Initial deposit of 1000 RUB",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, initialAmount),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, initialAmount)
                )

        ), UUID.randomUUID().toString());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);


        UUID firstReferenceId = UUID.randomUUID();
        UUID secondReferenceId = UUID.randomUUID();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<UUID> firstRequest = executor.submit(() ->
                    reserve(
                            ready,
                            start,
                            accounts.available().getId(),
                            accounts.reserved().getId(),
                            reserveAmount,
                            firstReferenceId
                    )
            );
            Future<UUID> secondRequest = executor.submit(() ->
                    reserve(
                            ready,
                            start,
                            accounts.available().getId(),
                            accounts.reserved().getId(),
                            reserveAmount,
                            secondReferenceId
                    )
            );

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            start.countDown();

            UUID firstResult = firstRequest.get(15, TimeUnit.SECONDS);
            UUID secondResult = secondRequest.get(15, TimeUnit.SECONDS);

            List<UUID> successfulEntries = Stream.of(firstResult, secondResult)
                    .filter(Objects::nonNull)
                    .toList();

            assertThat(successfulEntries).hasSize(1);

            UUID successfulEntryId = successfulEntries.getFirst();

            UUID failedReferenceId = firstResult == null ? firstReferenceId : secondReferenceId;

            assertThat(journalEntryRepository.findById(successfulEntryId)).isPresent();

            assertThat(journalEntryRepository
                    .findByReferenceTypeAndReferenceId("PAYMENT", failedReferenceId))
                    .isEmpty();

            assertThat(postingRepository.findAllByJournalEntryId(successfulEntryId)).hasSize(2);

            assertAccountBalance(accounts.cash().getId(), initialAmount);
            assertAccountBalance(accounts.available().getId(), BigDecimal.ZERO);
            assertAccountBalance(accounts.reserved().getId(), reserveAmount);
        }
    }

    private UUID reserve(
            CountDownLatch ready,
            CountDownLatch start,
            UUID availableAccountId,
            UUID reservedAccountId,
            BigDecimal reserveAmount,
            UUID referenceId
    ) throws Exception {

        ready.countDown();

        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request start timed out");

        try {
            return ledgerService.postJournalEntry(new PostJournalEntryCommand(
                    JournalEntryType.PAYMENT_RESERVE,
                    CURRENCY,
                    "PAYMENT",
                    referenceId,
                    "Reserve 1000 RUB",
                    List.of(
                            new PostingLineCommand(availableAccountId, PostingSide.DEBIT, reserveAmount),
                            new PostingLineCommand(reservedAccountId, PostingSide.CREDIT, reserveAmount)
                    )

            ), UUID.randomUUID().toString());
        } catch (InsufficientAccountBalanceException exception) {
            return null;
        }
    }

    @Test
    void checkPostingToClosedAccount() {
        TestAccounts accounts = createTestAccounts();

        accountService.closeAccount(accounts.available().getId());

        PostJournalEntryCommand command = new PostJournalEntryCommand(
                JournalEntryType.PAYMENT_RESERVE,
                CURRENCY,
                "PAYMENT",
                accounts.referenceId(),
                "Reserve payment",
                List.of(
                        new PostingLineCommand(accounts.available().getId(), PostingSide.DEBIT, new BigDecimal("1000")),
                        new PostingLineCommand(accounts.reserved().getId(), PostingSide.CREDIT, new BigDecimal("1000"))
                )
        );

        assertThatThrownBy(() -> ledgerService.postJournalEntry(command, UUID.randomUUID().toString())).isInstanceOf(AccountNotOpenException.class);

        assertThat(journalEntryRepository
                .findByReferenceTypeAndReferenceId("PAYMENT", accounts.referenceId()))
                .isEmpty();

        assertThat(postingRepository
                .findAllByJournalEntry_ReferenceId(accounts.referenceId()))
                .isEmpty();

        assertAccountBalance(accounts.available().getId(), BigDecimal.ZERO);
        assertAccountBalance(accounts.reserved().getId(), BigDecimal.ZERO);

        Account closedAccount = accountRepository.findById(accounts.available().getId()).orElseThrow();

        assertThat(closedAccount.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

}
