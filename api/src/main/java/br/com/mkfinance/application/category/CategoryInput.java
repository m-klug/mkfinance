package br.com.mkfinance.application.category;

import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Pillar;
import java.util.UUID;

/** Service-layer command, decoupled from the api-layer {@code CategoryRequest} DTO. */
public record CategoryInput(
        String name,
        CategoryType type,
        Pillar pillar,
        UUID parentId,
        String color,
        String icon) {
}
