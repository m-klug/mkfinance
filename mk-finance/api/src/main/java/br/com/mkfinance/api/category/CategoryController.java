package br.com.mkfinance.api.category;

import br.com.mkfinance.application.category.CategoryService;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces/{workspaceId}/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<CategoryResponse> list(@PathVariable UUID workspaceId, @RequestParam(required = false) CategoryType type) {
        return categoryService.list(workspaceContext.getWorkspaceId(), type).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody CategoryRequest request) {
        return mapper.toResponse(categoryService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request)));
    }

    @PatchMapping("/{categoryId}")
    public CategoryResponse update(
            @PathVariable UUID workspaceId, @PathVariable UUID categoryId, @Valid @RequestBody CategoryRequest request) {
        return mapper.toResponse(
                categoryService.update(workspaceContext.getWorkspaceId(), categoryId, mapper.toInput(request)));
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID categoryId) {
        categoryService.delete(workspaceContext.getWorkspaceId(), categoryId);
    }
}
