package br.com.mkfinance.infrastructure.security;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.MemberId;
import br.com.mkfinance.infrastructure.persistence.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The central workspace-isolation gate (PLANEJAMENTO §5 R8). Registered (see
 * {@code WebMvcConfig}) for every route matching {@code /workspaces/{workspaceId}/**}. Runs
 * after Spring Security's filter chain has already required authentication, so
 * {@link SecurityUtils#currentUserId()} is always available here.
 *
 * <p>Resolves the caller's {@code Member} row for the path's workspaceId and exposes it via the
 * request-scoped {@link WorkspaceContext}. No membership -> {@link AppException#notAMember()}
 * (403 NOT_A_MEMBER) — this also covers a non-existent workspaceId, deliberately not
 * distinguishing "workspace doesn't exist" from "you're not a member of it".
 */
@RequiredArgsConstructor
public class WorkspaceAccessInterceptor implements HandlerInterceptor {

    private final MemberRepository memberRepository;
    private final WorkspaceContext workspaceContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> pathVariables) || !pathVariables.containsKey("workspaceId")) {
            return true;
        }
        UUID workspaceId = parseWorkspaceId(String.valueOf(pathVariables.get("workspaceId")));
        UUID userId = SecurityUtils.currentUserId();

        Member member = memberRepository.findById(new MemberId(workspaceId, userId))
                .orElseThrow(AppException::notAMember);

        workspaceContext.resolve(workspaceId, userId, member.getRole());
        return true;
    }

    private UUID parseWorkspaceId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw AppException.notAMember();
        }
    }
}
