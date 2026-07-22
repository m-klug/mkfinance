package br.com.mkfinance.api.card;

import java.util.UUID;

/**
 * Contract: {@code Card}.
 *
 * @param availableLimitCents limit − everything still owed on statements that are not fully paid;
 *     may be negative when the card is over its limit
 */
public record CardResponse(
        UUID id,
        String name,
        String brand,
        long limitCents,
        Long availableLimitCents,
        short closingDay,
        short dueDay,
        UUID defaultPaymentAccountId,
        String color,
        boolean archived) {
}
