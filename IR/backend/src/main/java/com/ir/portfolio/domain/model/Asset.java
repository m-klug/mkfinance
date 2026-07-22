package com.ir.portfolio.domain.model;

import java.util.UUID;

public record Asset(
        UUID id,
        String ticker,
        String name,
        Market market,
        AssetCategory category
) {
    public boolean isUs() {
        return market == Market.US;
    }
}
