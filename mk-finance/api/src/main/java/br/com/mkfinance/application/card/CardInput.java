package br.com.mkfinance.application.card;

import java.util.UUID;

/** Validated payload for creating/updating a {@link br.com.mkfinance.domain.model.Card}. */
public record CardInput(
        String name,
        String brand,
        long limitCents,
        short closingDay,
        short dueDay,
        UUID defaultPaymentAccountId,
        String color) {
}
