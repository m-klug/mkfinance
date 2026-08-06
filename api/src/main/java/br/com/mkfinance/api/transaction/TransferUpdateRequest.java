package br.com.mkfinance.api.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code TransferUpdateRequest} — editing a transfer through either of its legs.
 *
 * <p>Deliberately not {@code TransactionRequest}: that schema requires a {@code categoryId} and
 * enumerates {@code type} as INCOME/EXPENSE, and a transfer has neither a category nor a mutable
 * type. Keeping the two payloads apart lets {@code TransactionRequest} stay strict (an INCOME or
 * EXPENSE really must be classified) while still leaving transfers editable by a generated client.
 *
 * <p>Nullable fields mean "leave as is": {@code accountId} keeps the leg on its current account,
 * {@code status} keeps the current one, and {@code contributionPillar} keeps whichever pillar the
 * transfer already carries (R9). The shared facts — amount, date and description — are required,
 * because the engine writes them to both legs unconditionally.
 *
 * <p>{@code vaultId} re-points the leg being edited at another vault, mirroring what {@code accountId}
 * does for the account. Omit to carry the current vault forward — unless {@code accountId} moved the
 * leg to a different account, in which case the old vault belongs to the old account and the
 * requirement runs afresh against the new one (DESENVOLVIMENTO-CAIXINHAS.md §4.4).
 */
public record TransferUpdateRequest(
        UUID accountId,
        @NotNull @Positive Long amountCents,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 255) String description,
        String note,
        TransactionStatus status,
        ContributionPillar contributionPillar,
        UUID vaultId) {
}
