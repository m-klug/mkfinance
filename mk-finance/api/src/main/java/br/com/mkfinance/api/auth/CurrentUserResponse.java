package br.com.mkfinance.api.auth;

import br.com.mkfinance.api.workspace.WorkspaceResponse;
import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id, String name, String email, String locale, List<WorkspaceResponse> workspaces) {
}
