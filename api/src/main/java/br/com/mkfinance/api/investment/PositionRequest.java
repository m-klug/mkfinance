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
 * Contract schema {@code PositionRequest}. Exactly one origin: {@code contributionTransactionId}
 * (the purchase transfer), or {@code accountId} + {@code principalCents} + {@code purchaseDate} (a
 * title bought before the app); anything else answers POSITION_ORIGIN_INVALID.
 *
 * @param rate % a.a. for PREFIXED; % of the CDI/Selic for the other indexers (110 means 110%)
 * @param vaultId where the yield goes on payment; optional
 */
public record PositionRequest(
        UUID contributionTransactionId,
        UUID accountId,
        @Positive Long principalCents,
        LocalDate purchaseDate,
        UUID vaultId,
        @Size(max = 80) String name,
        @NotNull InstrumentType type,
        @NotNull Indexer indexer,
        @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal rate,
        @NotNull LocalDate maturityDate) {
}
