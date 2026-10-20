package br.com.mkfinance.application.account.statementimport;

import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * What the user reviews before anything is written: the movements of the file matched against what
 * the account already has. Nothing in it is persisted.
 *
 * @param bankId the bank code the file declares (e.g. 077)
 * @param accountLast4 last digits of the account number in the file, so the user can tell whether
 *     it is the account they picked
 * @param accountBalanceCents what the picked account holds in the app right now
 * @param ledgerBalanceCents the balance the bank reports at {@code ledgerBalanceDate}, or null
 * @param warnings machine codes the client translates; none of them blocks the import
 */
public record BankStatementImportPreview(
        String bankId,
        String accountLast4,
        LocalDate periodStart,
        LocalDate periodEnd,
        long accountBalanceCents,
        Long ledgerBalanceCents,
        LocalDate ledgerBalanceDate,
        List<Line> lines,
        List<Warning> warnings) {

    /** What the description of a movement says it is; only picks the default {@link Mode}. */
    public enum Kind {
        NORMAL,
        CARD_BILL_PAYMENT,
        INVESTMENT_OUT,
        INVESTMENT_IN
    }

    /** How the confirmed line is recorded. */
    public enum Mode {
        /** An income/expense with a category. */
        ENTRY,
        /** A transfer to/from another account of the workspace. */
        TRANSFER,
        /** A payment of a credit-card statement. */
        STATEMENT_PAYMENT
    }

    public enum Duplicate {
        NONE,
        /** The bank id is already on the account. */
        EXACT,
        /** Same day, amount and direction as something typed by hand. */
        POSSIBLE
    }

    /**
     * @param type {@code INCOME} when money enters the account, {@code EXPENSE} when it leaves
     * @param amountCents always positive
     * @param suggestedCategoryId category of the latest entry with the same description, or null
     * @param suggestedCounterpartAccountId the only investment account, for investment movements
     * @param suggestedStatementId the single statement due around the date whose open amount equals this
     *     line, for a card bill payment
     * @param statementCandidates open statements due around the date, for the user to pick from when
     *     there is no single match
     */
    public record Line(
            String externalId,
            LocalDate date,
            String description,
            TransactionType type,
            long amountCents,
            Kind kind,
            Mode mode,
            Duplicate duplicate,
            UUID suggestedCategoryId,
            UUID suggestedCounterpartAccountId,
            UUID suggestedStatementId,
            List<StatementCandidate> statementCandidates,
            boolean selectedByDefault) {
    }

    public record StatementCandidate(
            UUID statementId, UUID cardId, String cardName, YearMonth referenceMonth, LocalDate dueDate, long remainingCents) {
    }

    /** @param detail what the warning is about (a bank id), or null */
    public record Warning(String code, String detail) {
    }
}
