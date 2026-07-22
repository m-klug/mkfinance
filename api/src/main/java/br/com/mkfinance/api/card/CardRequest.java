package br.com.mkfinance.api.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Contract: {@code CardRequest}.
 *
 * <p>Both days are capped at 28 — the contract's own bound — so that every reference month has the
 * day, February included, and {@code StatementAllocator} never has to invent a fallback.
 */
public record CardRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 20) String brand,
        @NotNull @PositiveOrZero Long limitCents,
        @NotNull @Min(1) @Max(28) Short closingDay,
        @NotNull @Min(1) @Max(28) Short dueDay,
        UUID defaultPaymentAccountId,
        @Size(max = 7) String color) {
}
