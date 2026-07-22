package br.com.mkfinance.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Shared JSON error writer for handlers that run inside the security filter chain. */
final class SecurityResponses {

    private SecurityResponses() {
    }

    static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("code", code));
    }
}
