package br.com.mkfinance.infrastructure.security;

import br.com.mkfinance.domain.model.User;
import br.com.mkfinance.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads the authentication principal by email. {@link UsernameNotFoundException} is translated
 * by {@code DaoAuthenticationProvider} into a generic {@code BadCredentialsException}
 * (hideUserNotFoundExceptions defaults to true), so unknown-email and wrong-password both surface
 * as INVALID_CREDENTIALS — never leaking which one it was.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user for email"));
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }
}
