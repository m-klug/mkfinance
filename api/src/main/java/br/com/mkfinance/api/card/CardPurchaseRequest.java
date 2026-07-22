package br.com.mkfinance.api.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Contract: {@code CardPurchaseRequest}. Omitting {@code installmentCount} means an outright purchase. */
public record CardPurchaseRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive Long totalAmountCents,
        @NotNull LocalDate purchaseDate,
        @Min(1) @Max(60) Short installmentCount) {
}
