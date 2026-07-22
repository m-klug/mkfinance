package br.com.mkfinance.api.pillar;

import br.com.mkfinance.domain.model.Pillar;

public record PillarAllocationResponse(Pillar pillar, int targetPercent) {
}
