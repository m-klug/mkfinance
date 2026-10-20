package br.com.mkfinance.api.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contract schema {@code FixedIncomeSimulationRequest}: one simulation, or two to compare.
 *
 * @param simulations one or two, compared when there are two
 */
public record FixedIncomeSimulationRequest(@NotEmpty @Size(max = 2) List<@Valid @NotNull Item> simulations) {

    /**
     * @param rate % a.a. for PREFIXED and IPCA_PLUS; % of the CDI/Selic for the other two
     * @param initialCents omitted means none
     * @param monthlyContributionCents omitted means none
     * @param startDate omitted means today
     * @param cdiAnnualPercent / selicAnnualPercent / ipcaAnnualPercent premises held constant for the
     *     whole term; each omitted one is filled from the stored market rates
     */
    public record Item(
            @NotNull InstrumentType type,
            @NotNull Indexer indexer,
            @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal rate,
            @Min(0) @Max(100_000_000_000L) Long initialCents,
            @Min(0) @Max(100_000_000_000L) Long monthlyContributionCents,
            @NotNull @Min(1) @Max(600) Integer months,
            LocalDate startDate,
            @DecimalMin("0") @DecimalMax("1000") BigDecimal cdiAnnualPercent,
            @DecimalMin("0") @DecimalMax("1000") BigDecimal selicAnnualPercent,
            @DecimalMin("-50") @DecimalMax("1000") BigDecimal ipcaAnnualPercent) {
    }
}
