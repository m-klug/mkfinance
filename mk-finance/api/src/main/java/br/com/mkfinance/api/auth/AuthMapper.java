package br.com.mkfinance.api.auth;

import br.com.mkfinance.api.workspace.WorkspaceResponse;
import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public CurrentUserResponse toResponse(User user, List<Member> memberships) {
        List<WorkspaceResponse> workspaces = memberships.stream()
                .map(m -> new WorkspaceResponse(m.getWorkspace().getId(), m.getWorkspace().getName(), m.getRole()))
                .toList();
        return new CurrentUserResponse(user.getId(), user.getName(), user.getEmail(), user.getLocale(), workspaces);
    }
}
