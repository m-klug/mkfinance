package br.com.mkfinance.api.workspace;

import java.time.OffsetDateTime;

public record InviteResponse(String token, OffsetDateTime expiresAt) {
}
