package tech.inita.ledgerservice;

import tech.inita.ledgerservice.account.Account;
import tech.inita.ledgerservice.account.AccountService;
import tech.inita.ledgerservice.account.AccountType;
import tech.inita.ledgerservice.account.OwnerType;

import java.util.UUID;

public class TestAccountFactory {

    private final AccountService accountService;

    public static final String DEFAULT_CURRENCY = "RUB";

    public TestAccountFactory(AccountService accountService) {
        this.accountService = accountService;
    }

    private Account createAccount(OwnerType ownerType, UUID ownerId, AccountType accountType, String currency) {
        return accountService.createAccount(
                ownerType,
                ownerId,
                accountType,
                currency,
                false
        );
    }

    public TestAccounts createTestAccounts(String currency) {
        UUID customerId = UUID.randomUUID();
        UUID platformId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();

        Account cashAccount = createAccount(OwnerType.PLATFORM, platformId, AccountType.CASH, currency);
        Account availableAccount = createAccount(OwnerType.CUSTOMER, customerId, AccountType.AVAILABLE, currency);
        Account reservedAccount = createAccount(OwnerType.CUSTOMER, customerId, AccountType.RESERVED, currency);

        return new TestAccounts(
                customerId,
                platformId,
                referenceId,
                cashAccount,
                availableAccount,
                reservedAccount,
                currency
        );
    }

    public TestAccounts createTestAccounts() {
        return createTestAccounts(DEFAULT_CURRENCY);
    }
}
