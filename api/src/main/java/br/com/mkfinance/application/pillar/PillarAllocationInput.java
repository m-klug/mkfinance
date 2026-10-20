package br.com.mkfinance.application.pillar;

import br.com.mkfinance.domain.model.Pillar;

/** Service-layer command, decoupled from the api-layer {@code PillarAllocationsRequest} DTO. */
public record PillarAllocationInput(Pillar pillar, int targetPercent) {
}
