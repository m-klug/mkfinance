package br.com.mkfinance.api.category;

import br.com.mkfinance.application.category.CategoryInput;
import br.com.mkfinance.domain.model.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryInput toInput(CategoryRequest request) {
        return new CategoryInput(
                request.name(), request.type(), request.pillar(), request.parentId(), request.color(), request.icon());
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getPillar(),
                category.getParentId(),
                category.getColor(),
                category.getIcon(),
                category.isSystem());
    }
}
