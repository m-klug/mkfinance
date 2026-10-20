package br.com.mkfinance.api.investment;

import java.util.UUID;

/** Contract schema {@code PositionReturnTransferRequest}; a null id unlinks. */
public record PositionReturnTransferRequest(UUID returnTransferTransactionId) {
}
