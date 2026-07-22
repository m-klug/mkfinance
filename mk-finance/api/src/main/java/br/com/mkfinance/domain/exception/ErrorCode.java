package br.com.mkfinance.domain.exception;

/**
 * Machine-readable error codes returned in {@code ErrorResponse.code} (contracts/openapi.yaml).
 * Never localized text — the frontend translates these. Keep in sync with the "Known codes"
 * list documented on {@code ErrorResponse.code} in the contract.
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // auth
    public static final String EMAIL_ALREADY_USED = "EMAIL_ALREADY_USED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

    // workspace isolation
    public static final String NOT_A_MEMBER = "NOT_A_MEMBER";
    public static final String WORKSPACE_NOT_FOUND = "WORKSPACE_NOT_FOUND";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    // invites
    public static final String INVITE_EXPIRED = "INVITE_EXPIRED";

    // accounts
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String ACCOUNT_HAS_TRANSACTIONS = "ACCOUNT_HAS_TRANSACTIONS";
    public static final String INVALID_CONTRIBUTION_PILLAR = "INVALID_CONTRIBUTION_PILLAR";

    // categories
    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";
    public static final String CATEGORY_PILLAR_REQUIRED = "CATEGORY_PILLAR_REQUIRED";
    public static final String CATEGORY_PILLAR_FORBIDDEN = "CATEGORY_PILLAR_FORBIDDEN";
    public static final String SYSTEM_CATEGORY_READONLY = "SYSTEM_CATEGORY_READONLY";

    // transactions & transfers (BE-3)
    public static final String TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String TRANSACTION_ALREADY_SETTLED = "TRANSACTION_ALREADY_SETTLED";
    /** A transfer leg cannot be turned into an INCOME/EXPENSE row, nor the other way around. */
    public static final String TRANSACTION_TYPE_IMMUTABLE = "TRANSACTION_TYPE_IMMUTABLE";
    /** The category's type must match the transaction's (an EXPENSE cannot use an INCOME category). */
    public static final String CATEGORY_TYPE_MISMATCH = "CATEGORY_TYPE_MISMATCH";
    /** A transfer needs two distinct accounts. */
    public static final String INSUFFICIENT_TRANSFER_ACCOUNTS = "INSUFFICIENT_TRANSFER_ACCOUNTS";
    /** Contribution (R9) into an INVESTMENT/SAVINGS account with no pillar given and no account default. */
    public static final String CONTRIBUTION_PILLAR_REQUIRED = "CONTRIBUTION_PILLAR_REQUIRED";
    /**
     * The transaction is the debit side of a credit-card statement payment, so the extrato cannot
     * delete it on its own — undoing it would leave the statement claiming to be paid with no money
     * having moved. The client sends the user to the statement screen, which reverses both together.
     */
    public static final String TRANSACTION_LOCKED_BY_PAYMENT = "TRANSACTION_LOCKED_BY_PAYMENT";

    // credit card & statements (BE-4)
    public static final String CARD_NOT_FOUND = "CARD_NOT_FOUND";
    /** An archived card is history: it can be read, but nothing new may be charged to it. */
    public static final String CARD_ARCHIVED = "CARD_ARCHIVED";
    public static final String PURCHASE_NOT_FOUND = "PURCHASE_NOT_FOUND";
    public static final String STATEMENT_NOT_FOUND = "STATEMENT_NOT_FOUND";
    /** No such payment on this statement — unknown id, another workspace's, or another bill's. */
    public static final String STATEMENT_PAYMENT_NOT_FOUND = "STATEMENT_PAYMENT_NOT_FOUND";
    /** R6: a PAID statement is frozen — its installments cannot be edited, moved or deleted. */
    public static final String STATEMENT_IMMUTABLE = "STATEMENT_IMMUTABLE";
    public static final String STATEMENT_ALREADY_PAID = "STATEMENT_ALREADY_PAID";
    /** Paying more than what is still owed on the statement; the MVP has no credit balance. */
    public static final String PAYMENT_EXCEEDS_REMAINING = "PAYMENT_EXCEEDS_REMAINING";
    /** Nothing to pay: the statement has no installments allocated to it yet. */
    public static final String STATEMENT_EMPTY = "STATEMENT_EMPTY";

    // generic
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
