
package tech.inita.ledgerservice.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AccountControllerIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    private static final String REQUEST_BODY = """
            {
                "ownerType": "CUSTOMER",
                "ownerId": "11111111-1111-1111-1111-111111111111",
                "accountType": "AVAILABLE",
                "currency": "RUB",
                "allowNegative": false
            }
            """;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
    }

    @Test
    void shouldCreateOnlyOneAccountWhenRequestsAreConcurrent() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<Integer> firstRequest = executor.submit(
                    () -> sendRequest(ready, start)
            );

            Future<Integer> secondRequest = executor.submit(
                    () -> sendRequest(ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
            int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

            assertThat(List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(201, 409);

            assertThat(accountRepository.count()).isEqualTo(1);
        }
    }

    private int sendRequest(
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {

        ready.countDown();

        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request start timed out");
        }

        return mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REQUEST_BODY)
                )
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
