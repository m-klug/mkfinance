package br.com.mkfinance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An installment of a card as the statement importer sees it: enough to tell whether a line of a bill
 * is already there ({@code externalId} equal) or could be the same charge typed by hand.
 */
public record ImportCandidateRow(
        UUID installmentId,
        String description,
        LocalDate purchaseDate,
        short number,
        short installmentCount,
        long amountCents,
        String externalId) {
}
