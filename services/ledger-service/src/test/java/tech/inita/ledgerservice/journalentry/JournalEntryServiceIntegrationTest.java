package tech.inita.ledgerservice.journalentry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.inita.ledgerservice.AbstractIntegrationTest;
import tech.inita.ledgerservice.TestAccountFactory;
import tech.inita.ledgerservice.TestAccounts;
import tech.inita.ledgerservice.TestConfig;
import tech.inita.ledgerservice.account.*;
import tech.inita.ledgerservice.account.exception.AccountNotOpenException;
import tech.inita.ledgerservice.account.exception.InsufficientAccountBalanceException;
import tech.inita.ledgerservice.idempotency.IdempotencyRecordRepository;
import tech.inita.ledgerservice.idempotency.IdempotencyScopes;
import tech.inita.ledgerservice.idempotency.exception.IdempotencyKeyReuseException;
import tech.inita.ledgerservice.journalentry.exception.UnbalancedJournalEntryException;
import tech.inita.ledgerservice.journalentry.command.PostJournalEntryCommand;
import tech.inita.ledgerservice.journalentry.command.PostingLineCommand;
import tech.inita.ledgerservice.posting.Posting;
import tech.inita.ledgerservice.posting.PostingRepository;
import tech.inita.ledgerservice.posting.PostingSide;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
@Import(TestConfig.class)
public class JournalEntryServiceIntegrationTest extends AbstractIntegrationTest{
    @Autowired
    JournalEntryService journalEntryService;

    @Autowired
    AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PostingRepository postingRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private TestAccountFactory testAccountFactory;

    private static final String CURRENCY = "RUB";

    private static final BigDecimal INITIAL_AMOUNT = BigDecimal.valueOf(1000);
    private static final BigDecimal RESERVE_AMOUNT = BigDecimal.valueOf(500);
    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

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

    @Test
    void postJournalEntrySuccessfulCheck() {

        TestAccounts accounts = testAccountFactory.createTestAccounts();

        UUID depositEntryId = journalEntryService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                "INITIAL_DEPOSIT",
                UUID.randomUUID(),
                "Initial deposit of 1000 RUB",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, INITIAL_AMOUNT),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, INITIAL_AMOUNT)
                )

        ), UUID.randomUUID().toString()).getId();


        assertAccountBalance(accounts.cash().getId(), INITIAL_AMOUNT);
        assertAccountBalance(accounts.available().getId(), INITIAL_AMOUNT);
        assertAccountBalance(accounts.reserved().getId(), BigDecimal.ZERO);


        UUID reserveEntryId = journalEntryService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.PAYMENT_RESERVE,
                CURRENCY,
                "PAYMENT",
                UUID.randomUUID(),
                "Reserve 500 RUB",
                List.of(
                        new PostingLineCommand(accounts.available().getId(), PostingSide.DEBIT, RESERVE_AMOUNT),
                        new PostingLineCommand(accounts.reserved().getId(), PostingSide.CREDIT, RESERVE_AMOUNT)
                )

        ), UUID.randomUUID().toString()).getId();

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

        TestAccounts accounts = testAccountFactory.createTestAccounts();
        String referenceType = "INITIAL_DEPOSIT";

        assertThrows(UnbalancedJournalEntryException.class, () -> journalEntryService.postJournalEntry(new PostJournalEntryCommand(
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
        TestAccounts accounts = testAccountFactory.createTestAccounts();
        String referenceType = "PAYMENT";

        String idempotencyKey = UUID.randomUUID().toString();

        assertThrows(InsufficientAccountBalanceException.class, () -> journalEntryService.postJournalEntry(new PostJournalEntryCommand(
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

        ), idempotencyKey));

        assertThat(idempotencyRecordRepository.findByScopeAndIdempotencyKey(IdempotencyScopes.LEDGER_POST_JOURNAL_ENTRY, idempotencyKey)).isEmpty();

        assertThat(journalEntryRepository.findByReferenceTypeAndReferenceId(referenceType, accounts.referenceId())).isEmpty();
        assertThat(postingRepository.findAllByJournalEntry_ReferenceId(accounts.referenceId())).isEmpty();

        assertAccountBalance(accounts.cash().getId(), BigDecimal.ZERO);
        assertAccountBalance(accounts.available().getId(), BigDecimal.ZERO);
        assertAccountBalance(accounts.reserved().getId(), BigDecimal.ZERO);
    }


    void concurrentReserve(boolean useSameIdempotencyKey) throws Exception {

        TestAccounts accounts = testAccountFactory.createTestAccounts();
        BigDecimal initialAmount = BigDecimal.valueOf(1000);
        BigDecimal reserveAmount = BigDecimal.valueOf(1000);

        journalEntryService.postJournalEntry(new PostJournalEntryCommand(
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

        String idempotencyKey = UUID.randomUUID().toString();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<UUID> firstRequest = executor.submit(() ->
                    reserve(
                            ready,
                            start,
                            accounts.available().getId(),
                            accounts.reserved().getId(),
                            reserveAmount,
                            firstReferenceId,
                            useSameIdempotencyKey?idempotencyKey:UUID.randomUUID().toString()
                    )
            );
            Future<UUID> secondRequest = executor.submit(() ->
                    reserve(
                            ready,
                            start,
                            accounts.available().getId(),
                            accounts.reserved().getId(),
                            reserveAmount,
                            secondReferenceId,
                            useSameIdempotencyKey?idempotencyKey:UUID.randomUUID().toString()
                    )
            );

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

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
            UUID referenceId,
            String idempotencyKey
    ) throws Exception {

        ready.countDown();

        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request start timed out");

        try {
            return journalEntryService.postJournalEntry(new PostJournalEntryCommand(
                    JournalEntryType.PAYMENT_RESERVE,
                    CURRENCY,
                    "PAYMENT",
                    referenceId,
                    "Reserve 1000 RUB",
                    List.of(
                            new PostingLineCommand(availableAccountId, PostingSide.DEBIT, reserveAmount),
                            new PostingLineCommand(reservedAccountId, PostingSide.CREDIT, reserveAmount)
                    )

            ), idempotencyKey).getId();
        } catch (InsufficientAccountBalanceException exception) {
            return null;
        }
    }

    @Test
    void checkConcurrentReserveWithDifferentIdempotencyKey() throws Exception {
        concurrentReserve(false);
    }

    @Test
    void checkPostingToClosedAccount() {
        TestAccounts accounts = testAccountFactory.createTestAccounts();

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

        assertThatThrownBy(() -> journalEntryService.postJournalEntry(command, UUID.randomUUID().toString())).isInstanceOf(AccountNotOpenException.class);

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

    @Test
    void shouldExecuteOperationOnlyOnceForSameIdempotencyKey() {

        TestAccounts accounts = testAccountFactory.createTestAccounts();
        String idempotencyKey = UUID.randomUUID().toString();

        BigDecimal initialAmount = new BigDecimal("1000");
        String referenceType = "DEPOSIT";

        PostJournalEntryCommand command = new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                referenceType,
                accounts.referenceId(),
                "Deposit of 1000 rub",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, initialAmount),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, initialAmount)
                )
        );

        UUID firstJournalEntryId = journalEntryService.postJournalEntry(command,  idempotencyKey).getId();
        UUID secondJournalEntryId = journalEntryService.postJournalEntry(command,  idempotencyKey).getId();

        assertExecuteOperationOnlyOnce(
                firstJournalEntryId,
                secondJournalEntryId,
                referenceType,
                accounts.referenceId(),
                accounts.cash().getId(),
                accounts.available().getId(),
                initialAmount,
                initialAmount
        );
    }

    void assertExecuteOperationOnlyOnce(
            UUID firstJournalEntryId,
            UUID secondJournalEntryId,
            String referenceType,
            UUID referenceId,
            UUID firstAccountId,
            UUID secondAccountId,
            BigDecimal expectFirstAccountBalance,
            BigDecimal expectSecondAccountBalance
    ) {
        assertThat(firstJournalEntryId).isEqualByComparingTo(secondJournalEntryId);

        assertThat(journalEntryRepository
                .findByReferenceTypeAndReferenceId(referenceType, referenceId))
                .isPresent()
                .get()
                .extracting(JournalEntry::getId)
                .isEqualTo(firstJournalEntryId);

        assertThat(postingRepository.findAllByJournalEntryId(firstJournalEntryId))
                .hasSize(2);

        assertThat(accountRepository.findById(firstAccountId))
                .get()
                .extracting(Account::getBalance, BIG_DECIMAL)
                .isEqualByComparingTo(expectFirstAccountBalance);
        assertThat(accountRepository.findById(secondAccountId))
                .get()
                .extracting(Account::getBalance, BIG_DECIMAL)
                .isEqualByComparingTo(expectSecondAccountBalance);
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequest() {

        TestAccounts accounts = testAccountFactory.createTestAccounts();
        String idempotencyKey = UUID.randomUUID().toString();

        BigDecimal initialCashBalance = accounts.cash().getBalance();
        BigDecimal initialAvailableBalance = accounts.available().getBalance();

        BigDecimal depositAmount1 = new BigDecimal("1000");
        BigDecimal depositAmount2 = new BigDecimal("2000");
        String referenceType = "DEPOSIT";

        PostJournalEntryCommand command1 = new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                referenceType,
                accounts.referenceId(),
                "Deposit of 1000 rub",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, depositAmount1),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, depositAmount1)
                )
        );

        PostJournalEntryCommand command2 = new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                referenceType,
                accounts.referenceId(),
                "Deposit of 2000 rub",
                List.of(
                        new PostingLineCommand(accounts.cash().getId(), PostingSide.DEBIT, depositAmount2),
                        new PostingLineCommand(accounts.available().getId(), PostingSide.CREDIT, depositAmount2)
                )
        );

        journalEntryService.postJournalEntry(command1,  idempotencyKey);

        assertThatThrownBy(() -> journalEntryService.postJournalEntry(command2,  idempotencyKey))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        BigDecimal actualCashBalance = accountRepository
                .findById(accounts.cash().getId())
                .orElseThrow()
                .getBalance();

        BigDecimal actualAvailableBalance = accountRepository
                .findById(accounts.available().getId())
                .orElseThrow()
                .getBalance();

        assertThat(actualCashBalance).isEqualByComparingTo(initialCashBalance.add(depositAmount1));
        assertThat(actualAvailableBalance).isEqualByComparingTo(initialAvailableBalance.add(depositAmount1));
    }

    @Test
    void shouldExecuteOnlyOnceForConcurrentRequests() throws Exception {

        TestAccounts accounts = testAccountFactory.createTestAccounts();
        BigDecimal initialAmount = BigDecimal.valueOf(1000);
        BigDecimal reserveAmount = BigDecimal.valueOf(1000);

        journalEntryService.postJournalEntry(new PostJournalEntryCommand(
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

        String idempotencyKey = UUID.randomUUID().toString();
        String referenceType = "PAYMENT";


        PostJournalEntryCommand command = new PostJournalEntryCommand(
                JournalEntryType.PAYMENT_RESERVE,
                CURRENCY,
                referenceType,
                accounts.referenceId(),
                "Reserve 1000 RUB",
                List.of(
                        new PostingLineCommand(accounts.available().getId(), PostingSide.DEBIT, reserveAmount),
                        new PostingLineCommand(accounts.reserved().getId(), PostingSide.CREDIT, reserveAmount)
                )

        );

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<UUID> firstRequest = executor.submit(() -> reserve2(ready, start, command, idempotencyKey));
            Future<UUID> secondRequest = executor.submit(() -> reserve2(ready, start, command, idempotencyKey));

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            UUID firstResult = firstRequest.get(15, TimeUnit.SECONDS);
            UUID secondResult = secondRequest.get(15, TimeUnit.SECONDS);

            assertExecuteOperationOnlyOnce(
                    firstResult,
                    secondResult,
                    referenceType,
                    accounts.referenceId(),
                    accounts.available().getId(),
                    accounts.reserved().getId(),
                    BigDecimal.ZERO,
                    reserveAmount
            );
        }
    }

    private UUID reserve2(
            CountDownLatch ready,
            CountDownLatch start,
            PostJournalEntryCommand command,
            String idempotencyKey
    ) throws Exception {

        ready.countDown();

        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent request start timed out");

        try {
            return journalEntryService.postJournalEntry(command, idempotencyKey).getId();
        } catch (InsufficientAccountBalanceException | IdempotencyKeyReuseException exception) {
            return null;
        }
    }

}
