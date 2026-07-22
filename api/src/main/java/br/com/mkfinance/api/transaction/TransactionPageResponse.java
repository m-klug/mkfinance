package br.com.mkfinance.api.transaction;

import java.util.List;

/**
 * Contract schema {@code TransactionPage}. Deliberately not Spring's {@code Page}: its JSON shape
 * is unstable across versions and far wider than the four fields the contract declares.
 */
public record TransactionPageResponse(
        List<TransactionResponse> content, int page, int size, long totalElements) {
}
