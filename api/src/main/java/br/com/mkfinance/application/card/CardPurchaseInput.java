package br.com.mkfinance.application.card;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Validated payload for a card purchase.
 *
 * @param installmentCount 1 for an outright purchase; the contract caps it at 60
 */
public record CardPurchaseInput(
        UUID categoryId, String description, long totalAmountCents, LocalDate purchaseDate, short installmentCount) {
}
