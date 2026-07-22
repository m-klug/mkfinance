package br.com.mkfinance.application.category;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> list(UUID workspaceId, CategoryType type) {
        return type == null
                ? categoryRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)
                : categoryRepository.findByWorkspaceIdAndTypeOrderByNameAsc(workspaceId, type);
    }

    public Category create(UUID workspaceId, CategoryInput input) {
        validatePillar(input.type(), input.pillar());
        UUID parentId = resolveParentId(workspaceId, input.parentId());
        Category category = Category.builder()
                .workspaceId(workspaceId)
                .parentId(parentId)
                .name(input.name())
                .type(input.type())
                .pillar(input.pillar())
                .color(input.color())
                .icon(input.icon())
                .system(false)
                .build();
        return categoryRepository.save(category);
    }

    public Category update(UUID workspaceId, UUID categoryId, CategoryInput input) {
        Category category = getOwned(workspaceId, categoryId);
        if (category.isSystem()) {
            throw AppException.systemCategoryReadOnly();
        }
        validatePillar(input.type(), input.pillar());
        UUID parentId = resolveParentId(workspaceId, input.parentId());
        category.setName(input.name());
        category.setType(input.type());
        category.setPillar(input.pillar());
        category.setParentId(parentId);
        category.setColor(input.color());
        category.setIcon(input.icon());
        return categoryRepository.save(category);
    }

    /** CATEGORY_IN_USE if referenced by a subcategory, transaction or card purchase. */
    public void delete(UUID workspaceId, UUID categoryId) {
        Category category = getOwned(workspaceId, categoryId);
        if (category.isSystem()) {
            throw AppException.systemCategoryReadOnly();
        }
        if (isInUse(category)) {
            throw AppException.categoryInUse();
        }
        categoryRepository.delete(category);
    }

    private boolean isInUse(Category category) {
        UUID id = category.getId();
        return categoryRepository.existsChildCategory(id)
                || categoryRepository.existsTransactionForCategory(id)
                || categoryRepository.existsCardPurchaseForCategory(id);
    }

    private UUID resolveParentId(UUID workspaceId, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        return getOwned(workspaceId, parentId).getId();
    }

    private Category getOwned(UUID workspaceId, UUID categoryId) {
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(AppException::categoryNotFound);
    }

    /** EXPENSE requires a pillar, INCOME forbids one (PLANEJAMENTO §4; DB constraint backs this up). */
    private void validatePillar(CategoryType type, Pillar pillar) {
        if (type == CategoryType.EXPENSE && pillar == null) {
            throw AppException.categoryPillarRequired();
        }
        if (type == CategoryType.INCOME && pillar != null) {
            throw AppException.categoryPillarForbidden();
        }
    }
}
