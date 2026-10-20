package br.com.mkfinance.api.investment;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code PositionUpdateRequest}: replaces the editable facts of an active position,
 * so an omitted {@code vaultId} clears it. {@code principalCents} and {@code purchaseDate} only apply
 * to a position bought before the app; a position born from a purchase transfer takes them from it
 * and ignores these two.
 */
public record PositionUpdateRequest(
        @Positive Long principalCents,
        LocalDate purchaseDate,
        UUID vaultId,
        @Size(max = 80) String name,
        @NotNull InstrumentType type,
        @NotNull Indexer indexer,
        @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal rate,
        @NotNull LocalDate maturityDate) {
}
