package tech.inita.ledgerservice.account;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tech.inita.ledgerservice.account.exception.AccountAlreadyExistException;

@Repository
@RequiredArgsConstructor
public class AccountPersistenceAdapter {

    private static final String ACCOUNT_UNIQUE_CONSTRAINT =
            "uq_accounts_owner_type_currency";

    private final AccountRepository accountRepository;

    public Account save(Account account) {
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            if (isAccountUniqueConstraintViolation(ex)) {
                throw new AccountAlreadyExistException(ex);
            }

            throw ex;
        }
    }

    private boolean isAccountUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return ACCOUNT_UNIQUE_CONSTRAINT.equals(
                        violation.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }
}