package br.com.mkfinance.application.account.statementimport;

import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Mode;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One confirmed line. Which of the optional fields is required depends on {@code mode}: a category
 * for {@code ENTRY}, a counterpart account for {@code TRANSFER}, a statement for
 * {@code STATEMENT_PAYMENT}.
 *
 * @param externalId the bank's id for the movement (OFX FITID)
 * @param type {@code INCOME} when money entered the imported account, {@code EXPENSE} when it left
 * @param amountCents always positive
 * @param contributionPillar only for a transfer out into an investment account (R9); null takes the
 *     destination's default
 */
public record BankStatementImportItem(
        String externalId,
        Mode mode,
        TransactionType type,
        long amountCents,
        LocalDate date,
        String description,
        UUID categoryId,
        UUID counterpartAccountId,
        ContributionPillar contributionPillar,
        UUID statementId) {
}
