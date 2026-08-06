package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-layer command for editing an existing transfer (R4), symmetric to {@link TransferInput}
 * for creation. Kept apart from {@link TransactionInput} because a transfer has no category and its
 * type cannot change, so the INCOME/EXPENSE command has no business carrying transfer concepts.
 *
 * <p>Nullable fields mean "leave as is": {@code accountId} keeps the edited leg on its current
 * account, {@code status} keeps the current one, and {@code contributionPillar} keeps whichever
 * pillar the transfer already carries — an explicit value re-targets it (R9), and the engine refuses
 * a pillar aimed at a destination that takes no contribution. The shared facts (amount, date,
 * description) are required, since the engine writes them to both legs unconditionally.
 *
 * <p>{@code vaultId} re-points the <em>addressed</em> leg's vault — symmetric to {@code accountId},
 * which only ever moves that same leg (DESENVOLVIMENTO-CAIXINHAS.md §4.4). {@code null} means "carry
 * the current vault forward" while the leg stays on the same account; once {@code accountId} moves
 * it, the old vault is discarded and the requirement is re-evaluated against the new account.
 */
public record TransferUpdateInput(
        UUID accountId,
        long amountCents,
        LocalDate date,
        String description,
        String note,
        TransactionStatus status,
        ContributionPillar contributionPillar,
        UUID vaultId) {

    /**
     * Pre-Caixinhas shape — kept so {@code TransactionService.update} (BE-3, out of BE-10's
     * ownership) keeps compiling unmodified: a plain {@code PATCH /transactions} edit has no vault
     * concept, so {@code vaultId} defaults to {@code null} — "carry the current vault forward",
     * exactly the semantics an omitted value already carries above.
     */
    public TransferUpdateInput(
            UUID accountId,
            long amountCents,
            LocalDate date,
            String description,
            String note,
            TransactionStatus status,
            ContributionPillar contributionPillar) {
        this(accountId, amountCents, date, description, note, status, contributionPillar, null);
    }
}
