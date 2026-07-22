package br.com.mkfinance.api.workspace;

import br.com.mkfinance.domain.model.MemberRole;
import java.util.UUID;

public record WorkspaceResponse(UUID id, String name, MemberRole role) {
}
