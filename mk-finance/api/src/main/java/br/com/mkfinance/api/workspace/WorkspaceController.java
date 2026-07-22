package br.com.mkfinance.api.workspace;

import br.com.mkfinance.application.workspace.WorkspaceService;
import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.Workspace;
import br.com.mkfinance.domain.model.WorkspaceInvite;
import br.com.mkfinance.infrastructure.security.SecurityUtils;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<WorkspaceResponse> list() {
        UUID userId = SecurityUtils.currentUserId();
        return workspaceService.listMemberships(userId).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        Workspace workspace = workspaceService.createWorkspace(userId, request.name());
        Member member = workspaceService.getMembership(workspace.getId(), userId);
        return mapper.toResponse(workspace, member);
    }

    /** {@code workspaceId} membership is already verified by WorkspaceAccessInterceptor. */
    @PostMapping("/{workspaceId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse createInvite(@PathVariable UUID workspaceId) {
        WorkspaceInvite invite = workspaceService.createInvite(workspaceContext.getWorkspaceId(), workspaceContext.getUserId());
        return mapper.toResponse(invite);
    }
}
