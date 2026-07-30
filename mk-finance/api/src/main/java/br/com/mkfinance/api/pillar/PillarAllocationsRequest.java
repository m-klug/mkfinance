package br.com.mkfinance.api.pillar;

import br.com.mkfinance.domain.model.Pillar;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Contract schema {@code PillarAllocationsRequest} — PUT {@code /pillar-allocations} replaces the
 * six targets from the current competency onward. All six pillars are required in one request (a
 * subset is rejected); {@code minItems}/{@code maxItems} = 6 in the contract, enforced here via
 * {@link Size}. Whether they add up to 100 is a business rule, not bean validation — it is checked
 * by {@code PillarAllocationService} and answered with 400 PILLAR_TARGETS_MUST_SUM_100.
 */
public record PillarAllocationsRequest(@NotNull @NotEmpty @Size(min = 6, max = 6) List<@Valid Item> allocations) {

    public record Item(@NotNull Pillar pillar, @NotNull @Min(0) @Max(100) Integer targetPercent) {
    }
}
