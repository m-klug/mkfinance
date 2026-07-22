package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filters of the extrato listing (PLANEJAMENTO §6.3). Every field is nullable and a null
 * simply means "don't filter by this".
 *
 * @param search free text matched case-insensitively against description and note
 */
public record TransactionFilter(
        LocalDate from,
        LocalDate to,
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        TransactionStatus status,
        String search) {

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null, null, null);
    }
}
