package tech.inita.ledgerservice.account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tech.inita.ledgerservice.account.exception.AccountNotFoundException;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AccountLockManager {
    private final AccountRepository accountRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Account> lockAccounts(Collection<UUID> accountIds) {
        Objects.requireNonNull(accountIds, "Account IDs must not be null");
        List<UUID> sortedIds = accountIds.stream()
                .map(id -> Objects.requireNonNull(id, "Account ID must not be null"))
                .distinct()
                .sorted()
                .toList();
        List<Account> accounts = new ArrayList<>(sortedIds.size());

        for (UUID id : sortedIds) {
            Account account = accountRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new AccountNotFoundException(id));

            accounts.add(account);
        }

        return List.copyOf(accounts);
    }
}
