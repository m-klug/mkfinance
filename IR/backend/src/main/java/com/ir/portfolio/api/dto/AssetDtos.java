package com.ir.portfolio.api.dto;

import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetRequest(
            @NotBlank @Size(max = 12) String ticker,
            @NotBlank @Size(max = 120) String name,
            @NotNull Market market,
            @NotNull AssetCategory category
    ) {
    }

    public record AssetResponse(
            UUID id,
            String ticker,
            String name,
            Market market,
            AssetCategory category
    ) {
    }
}
