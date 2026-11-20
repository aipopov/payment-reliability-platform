
package tech.inita.ledgerservice.account.dto;

import tech.inita.ledgerservice.account.Account;
import tech.inita.ledgerservice.account.AccountStatus;
import tech.inita.ledgerservice.account.AccountType;
import tech.inita.ledgerservice.account.OwnerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        OwnerType ownerType,
        UUID ownerId,
        AccountType accountType,
        String currency,
        BigDecimal balance,
        boolean allowNegative,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerType(),
                account.getOwnerId(),
                account.getAccountType(),
                account.getCurrency(),
                account.getBalance(),
                account.isAllowNegative(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
