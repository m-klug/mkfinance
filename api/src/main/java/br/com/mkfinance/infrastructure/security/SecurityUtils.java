package br.com.mkfinance.infrastructure.security;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** The authenticated user's id, resolved from the {@link AppUserPrincipal} in the context. */
    public static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw AppException.of(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        }
        return principal.getId();
    }
}
