package br.com.mkfinance.api.workspace;

import br.com.mkfinance.application.auth.AuthService;
import br.com.mkfinance.application.workspace.WorkspaceService;
import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.User;
import br.com.mkfinance.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
public class InviteController {

    private final WorkspaceService workspaceService;
    private final AuthService authService;
    private final WorkspaceMapper mapper;

    @PostMapping("/{token}/accept")
    public WorkspaceResponse accept(@PathVariable String token) {
        User user = authService.getById(SecurityUtils.currentUserId());
        Member member = workspaceService.acceptInvite(token, user);
        return mapper.toResponse(member);
    }
}
