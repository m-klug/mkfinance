package br.com.mkfinance.api.card;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contract: {@code CardPurchase} — the purchase together with the installments it generated. */
public record CardPurchaseResponse(
        UUID id,
        UUID cardId,
        UUID categoryId,
        String description,
        long totalAmountCents,
        LocalDate purchaseDate,
        short installmentCount,
        List<InstallmentResponse> installments) {

    /**
     * Contract: {@code Installment}.
     *
     * @param number 1-based; the cent remainder of the split lands on the last one (R1)
     * @param statementMonth reference month of the statement it was allocated to, e.g. {@code 2026-08}
     */
    public record InstallmentResponse(
            UUID id, short number, long amountCents, UUID statementId, String statementMonth) {
    }
}
