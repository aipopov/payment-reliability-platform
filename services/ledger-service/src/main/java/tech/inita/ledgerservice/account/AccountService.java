package tech.inita.ledgerservice.account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountPersistenceAdapter accountPersistenceAdapter;

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
}
