package tech.inita.ledgerservice.journalentry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tech.inita.ledgerservice.AbstractIntegrationTest;
import tech.inita.ledgerservice.TestAccountFactory;
import tech.inita.ledgerservice.TestAccounts;
import tech.inita.ledgerservice.TestConfig;
import tech.inita.ledgerservice.journalentry.dto.CreateJournalEntryRequest;
import tech.inita.ledgerservice.journalentry.dto.PostingLineRequest;
import tech.inita.ledgerservice.posting.PostingSide;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig.class)
public class JournalEntryControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAccountFactory testAccountFactory;

    private static final String JOURNAL_ENTRIES = "/api/v1/journal-entries";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private CreateJournalEntryRequest createBaseDepositJournalEntryRequest(TestAccounts accounts, BigDecimal amount) {
        return new CreateJournalEntryRequest(
                JournalEntryType.ADJUSTMENT,
                "RUB",
                "DEPOSIT",
                UUID.randomUUID(),
                "Deposit of %s RUB".formatted(amount),
                List.of(
                        new PostingLineRequest(
                                accounts.cash().getId(),
                                PostingSide.DEBIT,
                                amount
                        ),
                        new PostingLineRequest(
                                accounts.available().getId(),
                                PostingSide.CREDIT,
                                amount
                        )
                )
        );
    }

    @Test
    void shouldPostJournalEntry() throws Exception {

        TestAccounts accounts = testAccountFactory.createTestAccounts();

        CreateJournalEntryRequest request = createBaseDepositJournalEntryRequest(accounts, new BigDecimal("1000.00"));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.entryType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    void shouldReturnSameJournalEntryForRepeatedRequestWithSameIdempotencyKey() throws Exception {

        TestAccounts accounts = testAccountFactory.createTestAccounts();

        String idempotencyKey = UUID.randomUUID().toString();

        String requestContent = objectMapper.writeValueAsString(
                createBaseDepositJournalEntryRequest(accounts, new BigDecimal("1000.00"))
        );

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.entryType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.currency").value("RUB"));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.entryType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    void shouldRejectRequestWithDifferentPayloadForSameIdempotencyKey() throws Exception {
        TestAccounts accounts = testAccountFactory.createTestAccounts();

        String idempotencyKey = UUID.randomUUID().toString();

        String requestContent1 = objectMapper.writeValueAsString(
                createBaseDepositJournalEntryRequest(accounts, new BigDecimal("1000.00"))
        );

        String requestContent2 = objectMapper.writeValueAsString(
                createBaseDepositJournalEntryRequest(accounts, new BigDecimal("2000.00"))
        );

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.entryType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.currency").value("RUB"));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent2))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectRequestWithoutIdempotencyKey() throws Exception {
        TestAccounts accounts = testAccountFactory.createTestAccounts();

        String requestContent1 = objectMapper.writeValueAsString(
                createBaseDepositJournalEntryRequest(accounts, new BigDecimal("1000.00"))
        );

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectRequestWithBlankIdempotencyKey() throws Exception {
        TestAccounts accounts = testAccountFactory.createTestAccounts();

        String requestContent1 = objectMapper.writeValueAsString(
                createBaseDepositJournalEntryRequest(accounts, new BigDecimal("1000.00"))
        );

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectJournalEntryWhenAccountBalanceIsInsufficient() throws Exception {
        TestAccounts accounts = testAccountFactory.createTestAccounts();

        CreateJournalEntryRequest request = createBaseDepositJournalEntryRequest(accounts, new BigDecimal("1000.00"));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.entryType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.currency").value("RUB"));

        BigDecimal reserveAmount = BigDecimal.valueOf(1100);

        String reserveRequest = objectMapper.writeValueAsString(new CreateJournalEntryRequest(
                JournalEntryType.PAYMENT_RESERVE,
                "RUB",
                "RESERVE",
                UUID.randomUUID(),
                "Reserve of %s RUB".formatted(reserveAmount),
                List.of(
                        new PostingLineRequest(
                                accounts.available().getId(),
                                PostingSide.DEBIT,
                                reserveAmount
                        ),
                        new PostingLineRequest(
                                accounts.reserved().getId(),
                                PostingSide.CREDIT,
                                reserveAmount
                        )
                )
        ));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequest))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
        BigDecimal reserveAmount = BigDecimal.valueOf(1100);
        String reserveRequest = objectMapper.writeValueAsString(new CreateJournalEntryRequest(
                JournalEntryType.PAYMENT_RESERVE,
                "RUB",
                "RESERVE",
                UUID.randomUUID(),
                "Reserve of %s RUB".formatted(reserveAmount),
                List.of(
                        new PostingLineRequest(
                                UUID.randomUUID(),
                                PostingSide.DEBIT,
                                reserveAmount
                        ),
                        new PostingLineRequest(
                                UUID.randomUUID(),
                                PostingSide.CREDIT,
                                reserveAmount
                        )
                )
        ));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequest))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectJournalEntryWhenAccountCurrencyDoesNotMatch() throws Exception{
        TestAccounts accounts = testAccountFactory.createTestAccounts();
        BigDecimal amount = BigDecimal.valueOf(100);
        String reserveRequest = objectMapper.writeValueAsString(new CreateJournalEntryRequest(
                JournalEntryType.ADJUSTMENT,
                "USD",
                "DEPOSIT",
                UUID.randomUUID(),
                "Deposit of %s USD".formatted(amount),
                List.of(
                        new PostingLineRequest(
                                accounts.cash().getId(),
                                PostingSide.DEBIT,
                                amount
                        ),
                        new PostingLineRequest(
                                accounts.available().getId(),
                                PostingSide.CREDIT,
                                amount
                        )
                )
        ));

        mockMvc.perform(post(JOURNAL_ENTRIES)
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveRequest))
                .andExpect(status().isNotFound());
    }
}
