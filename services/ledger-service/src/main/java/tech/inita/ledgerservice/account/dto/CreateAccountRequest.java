package tech.inita.ledgerservice.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tech.inita.ledgerservice.account.AccountType;
import tech.inita.ledgerservice.account.OwnerType;

import java.util.UUID;

public record CreateAccountRequest(

        @NotNull
        OwnerType ownerType,

        @NotNull
        UUID ownerId,

        @NotNull
        AccountType accountType,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
        String currency,

        boolean allowNegative

) {
}
