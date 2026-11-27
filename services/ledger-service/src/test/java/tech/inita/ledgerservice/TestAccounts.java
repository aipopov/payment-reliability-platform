package tech.inita.ledgerservice;

import tech.inita.ledgerservice.account.Account;

import java.util.UUID;

public record TestAccounts(
        UUID customerId,
        UUID platformId,
        UUID referenceId,
        Account cash,
        Account available,
        Account reserved,
        String currency
) {
}
