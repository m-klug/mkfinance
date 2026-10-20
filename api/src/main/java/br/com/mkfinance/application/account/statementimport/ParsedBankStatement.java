package br.com.mkfinance.application.account.statementimport;

import java.time.LocalDate;
import java.util.List;

/**
 * A bank statement file, format-neutral.
 *
 * @param bankId the bank's code (OFX BANKID, e.g. 077), or null
 * @param accountId the account number as the bank writes it (sensitive: never logged, only its last
 *     digits are shown)
 * @param ledgerBalanceCents the balance the bank reports as of {@code ledgerBalanceDate}, or null
 */
public record ParsedBankStatement(
        String bankId,
        String accountId,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long ledgerBalanceCents,
        LocalDate ledgerBalanceDate,
        List<ParsedBankTransaction> transactions) {

    /**
     * One movement of the statement.
     *
     * @param externalId the bank's id for it (OFX FITID); unique within the account, not globally
     * @param amountCents signed: negative leaves the account
     * @param memo what the bank wrote about it (may carry third parties' names)
     * @param name the counterpart's name when the bank gives one, or null
     */
    public record ParsedBankTransaction(
            String externalId, LocalDate date, long amountCents, String memo, String name) {
    }
}
