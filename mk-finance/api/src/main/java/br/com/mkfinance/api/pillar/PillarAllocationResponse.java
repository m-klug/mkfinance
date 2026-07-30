package br.com.mkfinance.api.pillar;

import br.com.mkfinance.domain.model.Pillar;
import java.time.LocalDate;

/** Contract schema {@code PillarAllocation}. {@code effectiveFrom} is BE-6's versioning (V13). */
public record PillarAllocationResponse(Pillar pillar, int targetPercent, LocalDate effectiveFrom) {
}
