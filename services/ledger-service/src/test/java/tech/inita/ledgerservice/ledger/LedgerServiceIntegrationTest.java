package tech.inita.ledgerservice.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tech.inita.ledgerservice.account.*;
import tech.inita.ledgerservice.journalentry.JournalEntry;
import tech.inita.ledgerservice.journalentry.JournalEntryRepository;
import tech.inita.ledgerservice.journalentry.JournalEntryStatus;
import tech.inita.ledgerservice.journalentry.JournalEntryType;
import tech.inita.ledgerservice.ledger.command.PostJournalEntryCommand;
import tech.inita.ledgerservice.ledger.command.PostingLineCommand;
import tech.inita.ledgerservice.posting.Posting;
import tech.inita.ledgerservice.posting.PostingRepository;
import tech.inita.ledgerservice.posting.PostingSide;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class LedgerServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18-alpine")
                    .withDatabaseName("ledger_test")
                    .withUsername("ledger_user")
                    .withPassword("ledger_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    LedgerService ledgerService;

    @Autowired
    AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PostingRepository postingRepository;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID PLATFORM_ID = UUID.randomUUID();

    private static final String CURRENCY = "RUB";

    private static final BigDecimal INITIAL_AMOUNT = BigDecimal.valueOf(1000);
    private static final BigDecimal RESERVE_AMOUNT = BigDecimal.valueOf(500);

    @Autowired
    private JournalEntryRepository journalEntryRepository;

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

    @Test
    void postJournalEntrySuccessfulCheck() {

        Account cashAccount = createAccount(OwnerType.PLATFORM, PLATFORM_ID, AccountType.CASH);
        Account availableAccount = createAccount(OwnerType.CUSTOMER, CUSTOMER_ID, AccountType.AVAILABLE);
        Account reservedAccount = createAccount(OwnerType.CUSTOMER, CUSTOMER_ID, AccountType.RESERVED);

        UUID depositEntryId = ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.ADJUSTMENT,
                CURRENCY,
                "INITIAL_DEPOSIT",
                UUID.randomUUID(),
                "Initial deposit of 1000 RUB",
                List.of(
                        new PostingLineCommand(cashAccount.getId(), PostingSide.DEBIT, INITIAL_AMOUNT),
                        new PostingLineCommand(availableAccount.getId(), PostingSide.CREDIT, INITIAL_AMOUNT)
                )

        ));


        assertAccountBalance(cashAccount.getId(), INITIAL_AMOUNT);
        assertAccountBalance(availableAccount.getId(), INITIAL_AMOUNT);
        assertAccountBalance(reservedAccount.getId(), BigDecimal.ZERO);


        UUID reserveEntryId = ledgerService.postJournalEntry(new PostJournalEntryCommand(
                JournalEntryType.PAYMENT_RESERVE,
                CURRENCY,
                "PAYMENT",
                UUID.randomUUID(),
                "Reserve 500 RUB",
                List.of(
                        new PostingLineCommand(availableAccount.getId(), PostingSide.DEBIT, RESERVE_AMOUNT),
                        new PostingLineCommand(reservedAccount.getId(), PostingSide.CREDIT, RESERVE_AMOUNT)
                )

        ));

        assertAccountBalance(cashAccount.getId(), INITIAL_AMOUNT);
        assertAccountBalance(availableAccount.getId(), INITIAL_AMOUNT.subtract(RESERVE_AMOUNT));
        assertAccountBalance(reservedAccount.getId(), RESERVE_AMOUNT);

        assertThat(journalEntryRepository.findById(depositEntryId)).isPresent();
        assertThat(journalEntryRepository.findById(reserveEntryId)).isPresent();

        List<Posting> depositPostings = postingRepository.findAllByJournalEntryId(depositEntryId);
        List<Posting> reservePostings = postingRepository.findAllByJournalEntryId(reserveEntryId);

        assertThat(depositPostings).hasSize(2);
        assertThat(reservePostings).hasSize(2);

        assertThat(depositPostings).anySatisfy(posting ->
                assertPosting(posting, cashAccount.getId(), PostingSide.DEBIT, INITIAL_AMOUNT)
        );

        assertThat(depositPostings).anySatisfy(posting ->
            assertPosting(posting, availableAccount.getId(), PostingSide.CREDIT, INITIAL_AMOUNT)
        );

        assertThat(reservePostings).anySatisfy(posting ->
            assertPosting(posting, availableAccount.getId(), PostingSide.DEBIT, RESERVE_AMOUNT)
        );

        assertThat(reservePostings).anySatisfy(posting ->
            assertPosting(posting, reservedAccount.getId(), PostingSide.CREDIT, RESERVE_AMOUNT)
        );

        JournalEntry depositEntry = journalEntryRepository.findById(depositEntryId).orElseThrow();

        JournalEntry reserveEntry = journalEntryRepository.findById(reserveEntryId).orElseThrow();

        assertJournalEntry(depositEntry, depositEntryId, JournalEntryType.ADJUSTMENT);
        assertJournalEntry(reserveEntry, reserveEntryId, JournalEntryType.PAYMENT_RESERVE);
    }
}
