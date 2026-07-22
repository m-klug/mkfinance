package br.com.mkfinance.api.category;

import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Pillar;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType type,
        Pillar pillar,
        UUID parentId,
        String color,
        String icon,
        boolean system) {
}
