package br.com.mkfinance.application.card;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Validated payload for a card purchase.
 *
 * @param installmentCount 1 for an outright purchase; the contract caps it at 60
 * @param statementMonth when non-null, the statement the first installment lands on, overriding
 *     what the purchase date would compute — set by statement imports, where the bank has already
 *     decided which bill a line belongs to
 * @param externalId fingerprint of the statement line this purchase comes from; stored on its first installment
 * @param linkInstallmentId when non-null the line is not a new purchase: it is attached to this existing
 *     installment (the user typed the charge by hand) and nothing is created
 */
public record CardPurchaseInput(
        UUID categoryId,
        String description,
        long totalAmountCents,
        LocalDate purchaseDate,
        short installmentCount,
        YearMonth statementMonth,
        String externalId,
        UUID linkInstallmentId) {

    public CardPurchaseInput(
            UUID categoryId,
            String description,
            long totalAmountCents,
            LocalDate purchaseDate,
            short installmentCount,
            YearMonth statementMonth) {
        this(categoryId, description, totalAmountCents, purchaseDate, installmentCount, statementMonth, null, null);
    }

    public CardPurchaseInput(
            UUID categoryId, String description, long totalAmountCents, LocalDate purchaseDate, short installmentCount) {
        this(categoryId, description, totalAmountCents, purchaseDate, installmentCount, null);
    }
}
