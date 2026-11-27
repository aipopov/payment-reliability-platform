package tech.inita.ledgerservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.inita.ledgerservice.account.exception.*;
import tech.inita.ledgerservice.idempotency.exception.IdempotencyKeyReuseException;
import tech.inita.ledgerservice.journalentry.exception.JournalEntryCannotBePostedException;
import tech.inita.ledgerservice.journalentry.exception.JournalEntryNotFoundException;
import tech.inita.ledgerservice.journalentry.exception.UnbalancedJournalEntryException;
import tech.inita.ledgerservice.posting.exception.InvalidPostingException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ProblemDetail createProblem(
            HttpStatus status,
            String code,
            String detail
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);

        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);

        return problem;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InsufficientAccountBalanceException.class)
    public ProblemDetail handleInsufficientBalance(
            InsufficientAccountBalanceException exception
    ) {
        return createProblem(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_ACCOUNT_BALANCE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ProblemDetail handleIdempotencyKeyReuse(
            IdempotencyKeyReuseException exception
    ) {
        return createProblem(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(AccountAlreadyExistException.class)
    public ProblemDetail handleAccountAlreadyExist(
            AccountAlreadyExistException exception
    ) {
        return createProblem(
                HttpStatus.CONFLICT,
                "ACCOUNT_ALREADY_EXISTS",
                exception.getMessage()
        );
    }

    @ExceptionHandler(AccountHasNonZeroBalanceException.class)
    public ProblemDetail handleAccountHasNonZeroBalanceException(
            AccountHasNonZeroBalanceException exception
    ) {
        return createProblem(
                HttpStatus.CONFLICT,
                "ACCOUNT_HAS_NON_ZERO_BALANCE",
                exception.getMessage()
        );
    }

    @ExceptionHandler(AccountNotOpenException.class)
    public ProblemDetail handleAccountNotOpenException(
            AccountNotOpenException exception
    ) {
        return createProblem(
                HttpStatus.CONFLICT,
                "ACCOUNT_NOT_OPEN",
                exception.getMessage()
        );
    }

    @ExceptionHandler(JournalEntryCannotBePostedException.class)
    public ProblemDetail handleJournalEntryCannotBePostedException(
            JournalEntryCannotBePostedException exception
    ) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "JOURNAL_ENTRY_CANNOT_BE_POSTED",
                exception.getMessage()
        );
    }

    @ExceptionHandler(UnbalancedJournalEntryException.class)
    public ProblemDetail handleUnbalancedJournalEntryException(
            UnbalancedJournalEntryException exception
    ) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "UNBALANCE_JOURNAL_ENTRY",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidPostingException.class)
    public ProblemDetail handleInvalidPostingException(
            InvalidPostingException exception
    ) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "INVALID_POSTING",
                exception.getMessage()
        );
    }

    @ExceptionHandler(JournalEntryNotFoundException.class)
    public ProblemDetail handleJournalEntryNotFoundException(
            JournalEntryNotFoundException exception
    ) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "JOURNAL_ENTRY_NOT_FOUND",
                exception.getMessage()
        );
    }
}