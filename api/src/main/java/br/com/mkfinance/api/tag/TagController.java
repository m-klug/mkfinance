package br.com.mkfinance.api.tag;

import br.com.mkfinance.application.tag.TagService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code tags}. Like every workspace-scoped controller, queries use
 * {@link WorkspaceContext#getWorkspaceId()} — already verified by {@code WorkspaceAccessInterceptor}
 * — rather than the raw path variable, which is only declared so Spring MVC exposes it to the
 * interceptor.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final TagMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<TagResponse> list(@PathVariable UUID workspaceId) {
        return mapper.toResponseList(tagService.list(workspaceContext.getWorkspaceId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody TagRequest request) {
        return mapper.toResponse(tagService.create(workspaceContext.getWorkspaceId(), request.name()));
    }

    @PatchMapping("/{tagId}")
    public TagResponse update(
            @PathVariable UUID workspaceId, @PathVariable UUID tagId, @Valid @RequestBody TagRequest request) {
        return mapper.toResponse(
                tagService.rename(workspaceContext.getWorkspaceId(), tagId, request.name()));
    }

    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID tagId) {
        tagService.delete(workspaceContext.getWorkspaceId(), tagId);
    }
}
