package tech.inita.ledgerservice.account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountPersistenceAdapter accountPersistenceAdapter;
    private final AccountLockManager accountLockManager;

    @Transactional
    public Account createAccount(OwnerType ownerType, UUID ownerId, AccountType accountType, String currency, boolean allowNegative) {
        Account account = new Account(
                ownerType,
                ownerId,
                accountType,
                currency,
                allowNegative
        );

        return accountPersistenceAdapter.save(account);
    }

    @Transactional
    public void closeAccount(UUID accountId) {
        Account account = accountLockManager
                .lockAccounts(List.of(accountId))
                .getFirst();

        account.close();
    }
}
