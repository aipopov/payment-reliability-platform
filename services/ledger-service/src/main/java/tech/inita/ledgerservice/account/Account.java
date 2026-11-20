package tech.inita.ledgerservice.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 40)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AccountStatus status = AccountStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account(
            OwnerType ownerType,
            UUID ownerId,
            AccountType accountType,
            String currency,
            boolean allowNegative) {
        this.ownerType = Objects.requireNonNull(ownerType, "Owner type must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID must not be null");
        this.accountType = Objects.requireNonNull(accountType, "Account type must not be null");
        this.currency = Currency.getInstance(
                Objects.requireNonNull(currency, "Currency must not be null")
        ).getCurrencyCode();
        this.allowNegative = allowNegative;
    }

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();
        if (createdAt==null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }

}
