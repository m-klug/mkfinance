package br.com.mkfinance.domain.service;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * DESENVOLVIMENTO-CAIXINHAS.md §4.1/§4.2/§4.3 — the vault-ledger counterpart of
 * {@link BalanceCalculator}: a vault never holds money of its own, so its balance is always
 * derived from {@code vault_entry} rows the same way an account's is derived from
 * {@code transaction} rows.
 *
 * <p>Pure and stateless (no Spring, no repositories), fed either row-by-row or from pre-aggregated
 * SQL {@code GROUP BY} buckets — see {@code application/vault/VaultBalanceService}, which does the
 * latter so composing a whole account costs a handful of queries regardless of ledger size.
 *
 * <p>Three sources of {@code vault_entry} (§4.1/§5.5's table):
 *
 * <ul>
 *   <li><strong>CONTRIBUTION/WITHDRAWAL</strong> mirror a transfer leg 1:1 — {@code amount_cents} is
 *       {@code NULL} in the ledger, meaning "the paired transaction's signed amount". This class
 *       never reimplements that sign: {@link Entry#mirror} feeds the transaction's
 *       {@code (type, status, direction, amountCents)} straight into
 *       {@link BalanceCalculator#signedAmountCents}, literally reused.
 *   <li><strong>YIELD</strong> carries its own signed amount (always positive — a share of money
 *       that really arrived) and the transaction's status.
 *   <li><strong>REALLOCATION</strong> carries its own signed amount too, and has no status of its
 *       own: it is a reclassification that already happened, so it always counts as settled (§4.2)
 *       — callers build its {@link Entry} with {@link TransactionStatus#SETTLED} regardless of what
 *       is stored, since the column does not exist for this source.
 * </ul>
 */
public final class VaultBalanceCalculator {

    private VaultBalanceCalculator() {
    }

    /**
     * One vault-ledger row, or one pre-aggregated bucket, already reduced to a signed amount plus
     * the status that decides whether it counts toward the settled ("current") balance. Mirrors
     * {@link BalanceCalculator.Entry}'s role but one level up: by the time an {@code Entry} reaches
     * this class, CONTRIBUTION/WITHDRAWAL's mirrored sign has already been resolved by
     * {@link #mirror}, so {@link #balance} only ever sums plain signed cents.
     */
    public record Entry(long signedAmountCents, TransactionStatus status) {

        /**
         * A CONTRIBUTION or WITHDRAWAL bucket: {@code amountCents} is always positive (as stored on
         * {@code transaction}), and the sign comes from {@code type}/{@code direction} exactly as it
         * would for the account itself — {@link BalanceCalculator#signedAmountCents} is reused
         * literally, never reimplemented (armadilha 4, DESENVOLVIMENTO-CAIXINHAS.md §7).
         */
        public static Entry mirror(
                TransactionType type, TransactionStatus status, TransferDirection direction, long amountCents) {
            long signed = BalanceCalculator.signedAmountCents(
                    new BalanceCalculator.Entry(type, status, direction, amountCents));
            return new Entry(signed, status);
        }

        /** A YIELD or REALLOCATION bucket: already signed on the ledger itself. */
        public static Entry signed(long signedAmountCents, TransactionStatus status) {
            return new Entry(signedAmountCents, status);
        }
    }

    /**
     * A vault's settled ("current") and settled+pending ("projected") balance — reuses
     * {@link BalanceCalculator.Balance} rather than a parallel record, since a vault's balance means
     * exactly the same two things an account's does (R5).
     */
    public static Balance balance(Collection<Entry> entries) {
        long current = 0L;
        long projected = 0L;
        for (Entry entry : entries) {
            projected += entry.signedAmountCents();
            if (entry.status() == TransactionStatus.SETTLED) {
                current += entry.signedAmountCents();
            }
        }
        return new Balance(current, projected);
    }

    /**
     * The whole account composition (§4.3): every vault's balance plus the unallocated slice, at
     * both levels, with the identity <strong>Σ vaults + unallocated = account balance</strong>
     * asserted before returning — the same discipline as
     * {@code InstallmentSplitter.assertSumMatches}, so a future change that computes
     * {@code unallocatedCents} some other way (instead of by subtraction, as here) cannot silently
     * drift from the account's real balance.
     *
     * @param accountBalance the account's own balance (from {@code AccountBalanceService}), the
     *     ground truth every vault balance must add back up to
     * @param vaultBalances every non-archived vault's balance, keyed by vault id
     */
    public static Composition compose(Balance accountBalance, Map<UUID, Balance> vaultBalances) {
        long allocated = 0L;
        long allocatedProjected = 0L;
        for (Balance vaultBalance : vaultBalances.values()) {
            allocated += vaultBalance.currentCents();
            allocatedProjected += vaultBalance.projectedCents();
        }
        long unallocated = accountBalance.currentCents() - allocated;
        long unallocatedProjected = accountBalance.projectedCents() - allocatedProjected;

        assertIdentity(accountBalance.currentCents(), allocated, unallocated);
        assertIdentity(accountBalance.projectedCents(), allocatedProjected, unallocatedProjected);

        return new Composition(
                accountBalance.currentCents(),
                accountBalance.projectedCents(),
                allocated,
                unallocated,
                unallocatedProjected,
                vaultBalances);
    }

    /** Guards the one invariant every caller relies on (§4.2/§4.3), by construction here but cheap to check. */
    private static void assertIdentity(long accountCents, long allocatedCents, long unallocatedCents) {
        long sum = allocatedCents + unallocatedCents;
        if (sum != accountCents) {
            throw new IllegalStateException(
                    "Vault composition lost cents: allocated " + allocatedCents + " + unallocated "
                            + unallocatedCents + " = " + sum + " != account balance " + accountCents);
        }
    }

    /**
     * @param balanceCents the account's settled balance (R5) — ground truth
     * @param projectedBalanceCents the account's settled+pending balance (R5) — ground truth
     * @param allocatedCents sum of the vaults' settled balances
     * @param unallocatedCents {@code balanceCents - allocatedCents}; can go negative (§4.3, shown not blocked)
     * @param projectedUnallocatedCents the projected counterpart of {@code unallocatedCents}
     * @param vaultBalances every vault's own balance, keyed by vault id
     */
    public record Composition(
            long balanceCents,
            long projectedBalanceCents,
            long allocatedCents,
            long unallocatedCents,
            long projectedUnallocatedCents,
            Map<UUID, Balance> vaultBalances) {
    }
}
