package br.com.mkfinance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of a statement: the installment plus the purchase context the user needs to recognize it
 * ("Notebook 2/3", bought on the 14th). Projected in a single join so opening a statement does not
 * fan out into one purchase lookup per line.
 */
public record StatementItemRow(
        UUID installmentId,
        UUID purchaseId,
        String description,
        UUID categoryId,
        long amountCents,
        LocalDate purchaseDate,
        short installmentNumber,
        short installmentCount) {
}
