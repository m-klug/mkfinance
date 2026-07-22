package br.com.mkfinance.api.workspace;

import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.Workspace;
import br.com.mkfinance.domain.model.WorkspaceInvite;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceMapper {

    public WorkspaceResponse toResponse(Member member) {
        return new WorkspaceResponse(member.getWorkspace().getId(), member.getWorkspace().getName(), member.getRole());
    }

    public WorkspaceResponse toResponse(Workspace workspace, Member member) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), member.getRole());
    }

    public InviteResponse toResponse(WorkspaceInvite invite) {
        return new InviteResponse(invite.getToken(), invite.getExpiresAt());
    }
}
