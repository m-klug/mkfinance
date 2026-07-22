package br.com.mkfinance.infrastructure.security;

import br.com.mkfinance.domain.model.MemberRole;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped, per-request result of workspace-isolation resolution — the central mechanism
 * every other backend workstream (BE-3/BE-4/BE-5) must reuse instead of re-checking membership.
 *
 * <p>Resolved once by {@link WorkspaceAccessInterceptor} for any request whose route has a
 * {@code {workspaceId}} path variable (pattern {@code /workspaces/*}/**}). Inject this bean
 * (constructor injection, like any other singleton collaborator — Spring transparently proxies
 * it to the current request's instance) into a controller or service that needs the verified
 * workspace id / caller id / role instead of re-parsing the path variable or re-querying
 * membership:
 *
 * <pre>{@code
 * @RequiredArgsConstructor
 * class SomeController {
 *     private final WorkspaceContext workspaceContext;
 *
 *     @GetMapping("/workspaces/{workspaceId}/whatever")
 *     List<X> list(@PathVariable UUID workspaceId) {
 *         return service.list(workspaceContext.getWorkspaceId()); // already verified membership
 *     }
 * }
 * }</pre>
 *
 * <p>If a route is NOT under {@code /workspaces/{workspaceId}/**}, this bean is never resolved
 * for that request and any getter throws {@link IllegalStateException} — don't inject it into
 * endpoints outside that pattern.
 */
@Component
@RequestScope
public class WorkspaceContext {

    private UUID workspaceId;
    private UUID userId;
    private MemberRole role;
    private boolean resolved = false;

    public void resolve(UUID workspaceId, UUID userId, MemberRole role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.resolved = true;
    }

    public UUID getWorkspaceId() {
        requireResolved();
        return workspaceId;
    }

    public UUID getUserId() {
        requireResolved();
        return userId;
    }

    public MemberRole getRole() {
        requireResolved();
        return role;
    }

    public boolean isOwner() {
        return getRole() == MemberRole.OWNER;
    }

    public boolean isResolved() {
        return resolved;
    }

    private void requireResolved() {
        if (!resolved) {
            throw new IllegalStateException(
                    "WorkspaceContext accessed before resolution — this route must have a "
                            + "{workspaceId} path variable handled by WorkspaceAccessInterceptor.");
        }
    }
}
