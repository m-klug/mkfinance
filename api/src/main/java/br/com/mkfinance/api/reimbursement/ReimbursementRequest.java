package br.com.mkfinance.api.reimbursement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Contract schema {@code ReimbursementRequest}: how much of the income (in the path) pays this expense. */
public record ReimbursementRequest(@NotNull UUID expenseTransactionId, @NotNull @Positive Long amountCents) {
}
