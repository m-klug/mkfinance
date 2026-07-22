package br.com.mkfinance.api.category;

import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Pillar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull CategoryType type,
        Pillar pillar,
        UUID parentId,
        String color,
        String icon) {
}
