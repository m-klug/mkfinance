package br.com.mkfinance.api.tag;

import java.util.UUID;

/**
 * Contract schema {@code Tag}. Also what {@code TransactionResponse.tags} carries — a transaction's
 * tags and the workspace's tag list share this exact shape ({@code id}, {@code name}), so one DTO
 * serves both.
 */
public record TagResponse(UUID id, String name) {
}
