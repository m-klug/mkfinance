package br.com.mkfinance.api.auth;

import br.com.mkfinance.application.auth.AuthService;
import br.com.mkfinance.application.workspace.WorkspaceService;
import br.com.mkfinance.domain.model.User;
import br.com.mkfinance.infrastructure.security.AppUserPrincipal;
import br.com.mkfinance.infrastructure.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WorkspaceService workspaceService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuthMapper authMapper;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentUserResponse register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = authService.register(request.name(), request.email(), request.password());
        establishSession(authenticatedToken(user), httpRequest, httpResponse);
        return authMapper.toResponse(user, workspaceService.listMemberships(user.getId()));
    }

    @PostMapping("/login")
    public CurrentUserResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        establishSession(authentication, httpRequest, httpResponse);
        UUID userId = ((AppUserPrincipal) authentication.getPrincipal()).getId();
        User user = authService.getById(userId);
        return authMapper.toResponse(user, workspaceService.listMemberships(userId));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        UUID userId = SecurityUtils.currentUserId();
        User user = authService.getById(userId);
        return authMapper.toResponse(user, workspaceService.listMemberships(userId));
    }

    private Authentication authenticatedToken(User user) {
        AppUserPrincipal principal = new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
        List<GrantedAuthority> authorities = List.of();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    /** Persists the SecurityContext into the HTTP session so the response gets the session cookie. */
    private void establishSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
