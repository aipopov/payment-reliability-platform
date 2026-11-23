package tech.inita.ledgerservice.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tech.inita.ledgerservice.account.exception.AccountHasNonZeroBalanceException;
import tech.inita.ledgerservice.account.exception.AccountNotOpenException;
import tech.inita.ledgerservice.account.exception.InsufficientAccountBalanceException;
import tech.inita.ledgerservice.posting.PostingSide;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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


    public void applyPosting(PostingSide side, BigDecimal amount) {
        if (status != AccountStatus.OPEN) {
            throw new AccountNotOpenException(id);
        }

        if (side == null) {
            throw new IllegalArgumentException("Posting side must not be null");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Posting amount must be greater than zero"
            );
        }


        boolean increasesBalance = isIncreasesBalance(side);

        BigDecimal newBalance = increasesBalance
                ? balance.add(amount)
                : balance.subtract(amount);

        if (!allowNegative && newBalance.signum() < 0) {
            throw new InsufficientAccountBalanceException(id);
        }

        this.balance = newBalance;
    }

    private boolean isIncreasesBalance(PostingSide side) {
        NormalSide normalSide = accountType.getAccountClass().getNormalSide();

        return (side == PostingSide.DEBIT && normalSide == NormalSide.DEBIT)
                || (side == PostingSide.CREDIT && normalSide == NormalSide.CREDIT);
    }

    public void close() {
        if (status != AccountStatus.OPEN) throw new AccountNotOpenException(id);
        if (balance.compareTo(BigDecimal.ZERO) != 0) throw new AccountHasNonZeroBalanceException(id, balance);

        this.status = AccountStatus.CLOSED;
    }


}
