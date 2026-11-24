package tech.inita.ledgerservice.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountPersistenceAdapter accountPersistenceAdapter;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldCreateAccount() {
        UUID ownerId = UUID.randomUUID();

        when(accountPersistenceAdapter.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.createAccount(
                OwnerType.CUSTOMER,
                ownerId,
                AccountType.AVAILABLE,
                "RUB",
                false
        );

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);

        verify(accountPersistenceAdapter).save(captor.capture());

        Account savedAccount = captor.getValue();

        assertThat(result).isSameAs(savedAccount);
        assertThat(savedAccount.getOwnerType()).isEqualTo(OwnerType.CUSTOMER);
        assertThat(savedAccount.getOwnerId()).isEqualTo(ownerId);
        assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.AVAILABLE);
        assertThat(savedAccount.getCurrency()).isEqualTo("RUB");
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedAccount.isAllowNegative()).isFalse();
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.OPEN);

        verifyNoMoreInteractions(accountPersistenceAdapter);
    }
}