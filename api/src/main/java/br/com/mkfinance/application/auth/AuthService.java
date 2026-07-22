package br.com.mkfinance.application.auth;

import br.com.mkfinance.application.workspace.WorkspaceService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.User;
import br.com.mkfinance.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final String PERSONAL_WORKSPACE_NAME = "Espaço pessoal";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceService workspaceService;

    /** Creates the user, a personal workspace (caller as OWNER) and seeds its defaults (R8 setup). */
    public User register(String name, String email, String rawPassword) {
        String normalizedEmail = email.strip().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw AppException.emailAlreadyUsed(normalizedEmail);
        }
        User user = User.builder()
                .name(name)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .locale("pt-BR")
                .build();
        user = userRepository.save(user);
        workspaceService.createWorkspace(user, PERSONAL_WORKSPACE_NAME);
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(java.util.UUID id) {
        return userRepository.findById(id).orElseThrow(AppException::invalidCredentials);
    }
}
