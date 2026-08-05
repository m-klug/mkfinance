package br.com.mkfinance.domain.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Single exception type for business-rule / not-found / auth failures translated by
 * {@code br.com.mkfinance.api.error.GlobalExceptionHandler} into the contract's
 * {@code ErrorResponse} shape ({@code code} + optional {@code params}). Never carries a
 * human-readable / localized message — {@link #getCode()} is the only thing the client reads.
 */
@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> params;

    private AppException(String code, HttpStatus status, Map<String, Object> params) {
        super(code);
        this.code = code;
        this.status = status;
        this.params = params;
    }

    public static AppException of(String code, HttpStatus status) {
        return new AppException(code, status, Map.of());
    }

    public static AppException of(String code, HttpStatus status, Map<String, Object> params) {
        return new AppException(code, status, params);
    }

    public static AppException emailAlreadyUsed(String email) {
        return of(ErrorCode.EMAIL_ALREADY_USED, HttpStatus.CONFLICT, Map.of("email", email));
    }

    public static AppException invalidCredentials() {
        return of(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }

    public static AppException notAMember() {
        return of(ErrorCode.NOT_A_MEMBER, HttpStatus.FORBIDDEN);
    }

    public static AppException inviteExpired() {
        return of(ErrorCode.INVITE_EXPIRED, HttpStatus.GONE);
    }

    public static AppException accountNotFound() {
        return of(ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException accountHasTransactions() {
        return of(ErrorCode.ACCOUNT_HAS_TRANSACTIONS, HttpStatus.CONFLICT);
    }

    public static AppException invalidContributionPillar() {
        return of(ErrorCode.INVALID_CONTRIBUTION_PILLAR, HttpStatus.BAD_REQUEST);
    }

    public static AppException categoryNotFound() {
        return of(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException categoryInUse() {
        return of(ErrorCode.CATEGORY_IN_USE, HttpStatus.CONFLICT);
    }

    public static AppException categoryPillarRequired() {
        return of(ErrorCode.CATEGORY_PILLAR_REQUIRED, HttpStatus.BAD_REQUEST);
    }

    public static AppException categoryPillarForbidden() {
        return of(ErrorCode.CATEGORY_PILLAR_FORBIDDEN, HttpStatus.BAD_REQUEST);
    }

    public static AppException systemCategoryReadOnly() {
        return of(ErrorCode.SYSTEM_CATEGORY_READONLY, HttpStatus.CONFLICT);
    }

    public static AppException transactionNotFound() {
        return of(ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException transactionAlreadySettled() {
        return of(ErrorCode.TRANSACTION_ALREADY_SETTLED, HttpStatus.CONFLICT);
    }

    public static AppException transactionTypeImmutable() {
        return of(ErrorCode.TRANSACTION_TYPE_IMMUTABLE, HttpStatus.CONFLICT);
    }

    public static AppException categoryTypeMismatch() {
        return of(ErrorCode.CATEGORY_TYPE_MISMATCH, HttpStatus.BAD_REQUEST);
    }

    public static AppException insufficientTransferAccounts() {
        return of(ErrorCode.INSUFFICIENT_TRANSFER_ACCOUNTS, HttpStatus.BAD_REQUEST);
    }

    /**
     * The extrato tried to delete the debit side of a statement payment. Not a data-integrity
     * accident (the FK would have raised one anyway) but a rule with a way out: the client points the
     * user at the statement, where reversing removes payment and debit together.
     */
    public static AppException transactionLockedByPayment() {
        return of(ErrorCode.TRANSACTION_LOCKED_BY_PAYMENT, HttpStatus.CONFLICT);
    }

    public static AppException contributionPillarRequired() {
        return of(ErrorCode.CONTRIBUTION_PILLAR_REQUIRED, HttpStatus.BAD_REQUEST);
    }

    public static AppException cardNotFound() {
        return of(ErrorCode.CARD_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException cardArchived() {
        return of(ErrorCode.CARD_ARCHIVED, HttpStatus.CONFLICT);
    }

    public static AppException purchaseNotFound() {
        return of(ErrorCode.PURCHASE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException statementNotFound() {
        return of(ErrorCode.STATEMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException statementPaymentNotFound() {
        return of(ErrorCode.STATEMENT_PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    /**
     * R6 — the edit would touch an installment sitting on a statement already paid in full.
     *
     * @param statementMonth reference month (YYYY-MM) of the offending statement, so the client can
     *     tell the user <em>which</em> bill is blocking the change
     */
    public static AppException statementImmutable(String statementMonth) {
        return of(ErrorCode.STATEMENT_IMMUTABLE, HttpStatus.CONFLICT, Map.of("statementMonth", statementMonth));
    }

    public static AppException statementAlreadyPaid() {
        return of(ErrorCode.STATEMENT_ALREADY_PAID, HttpStatus.CONFLICT);
    }

    public static AppException paymentExceedsRemaining(long remainingCents) {
        return of(
                ErrorCode.PAYMENT_EXCEEDS_REMAINING,
                HttpStatus.CONFLICT,
                Map.of("remainingCents", remainingCents));
    }

    public static AppException statementEmpty() {
        return of(ErrorCode.STATEMENT_EMPTY, HttpStatus.CONFLICT);
    }

    /**
     * The six pillar targets do not add up to 100. The database checks each row is within 0..100 but
     * cannot check the set, so the sum is an application rule.
     *
     * @param sum what the payload actually added up to, so the client can say how far off it is
     */
    public static AppException pillarTargetsMustSum100(int sum) {
        return of(ErrorCode.PILLAR_TARGETS_MUST_SUM_100, HttpStatus.BAD_REQUEST, Map.of("sum", sum));
    }

    public static AppException recurrenceNotFound() {
        return of(ErrorCode.RECURRENCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException recurrenceOccurrenceNotFound() {
        return of(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException tagNameDuplicate(String name) {
        return of(ErrorCode.TAG_NAME_DUPLICATE, HttpStatus.CONFLICT, Map.of("name", name));
    }

    public static AppException tagNotFound() {
        return of(ErrorCode.TAG_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
}
