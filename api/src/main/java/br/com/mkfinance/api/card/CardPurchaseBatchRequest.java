package br.com.mkfinance.api.card;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contract: {@code CardPurchaseBatchRequest}. Every item is an outright purchase charged to
 * {@code statementMonth}, the bill the imported statement says it belongs to. {@code externalId} is the
 * line's fingerprint from the preview; with {@code linkInstallmentId} the line is attached to that
 * hand-typed installment instead of creating a purchase; {@code categoryId} is required only when it
 * creates one.
 */
public record CardPurchaseBatchRequest(
        @NotNull @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String statementMonth,
        @NotEmpty @Size(max = 500) List<@Valid @NotNull Item> items) {

    public record Item(
            UUID categoryId,
            @NotBlank @Size(max = 255) String description,
            @NotNull @Positive Long totalAmountCents,
            @NotNull LocalDate purchaseDate,
            @Size(max = 64) String externalId,
            UUID linkInstallmentId) {
    }
}
