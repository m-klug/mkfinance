package br.com.mkfinance.api.reimbursement;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Contract schema {@code Reimbursement} (ADR-0002). */
public record ReimbursementResponse(
        UUID id,
        UUID incomeTransactionId,
        UUID expenseTransactionId,
        long amountCents,
        OffsetDateTime createdAt) {
}
