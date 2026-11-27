package tech.inita.ledgerservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tech.inita.ledgerservice.account.AccountService;

@TestConfiguration
public class TestConfig {

    @Bean
    public TestAccountFactory testAccountFactory(AccountService accountService) {
        return new TestAccountFactory(accountService);
    }
}