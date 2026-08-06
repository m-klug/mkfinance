package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.service.YieldDistributor.Share;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Matches {@code components.schemas.VaultYield} in shape — the booked yield plus the split that was
 * actually applied. {@code shares} reuses {@link Share} directly (the same convention
 * {@code VaultBalanceCalculator.Balance}/{@code Composition} follow for the rest of this feature)
 * rather than a parallel record, since it means exactly the same two things here as it does inside
 * {@code YieldDistributor}.
 */
public record VaultYieldResult(UUID transactionId, long amountCents, LocalDate date, List<Share> shares) {
}
