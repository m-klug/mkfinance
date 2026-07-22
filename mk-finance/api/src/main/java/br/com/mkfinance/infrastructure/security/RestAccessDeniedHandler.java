package br.com.mkfinance.infrastructure.security;

import br.com.mkfinance.domain.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Denials raised inside the security filter chain itself (e.g. a missing/invalid CSRF token) ->
 * 403 with the contract's ErrorResponse shape. Distinct from the business-level NOT_A_MEMBER
 * (thrown as an AppException from WorkspaceAccessInterceptor and handled by
 * GlobalExceptionHandler, since that runs inside DispatcherServlet, after this filter-level
 * handler would already have let the request through).
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        SecurityResponses.writeError(response, objectMapper, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }
}
