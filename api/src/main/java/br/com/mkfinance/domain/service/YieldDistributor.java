package br.com.mkfinance.domain.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * DESENVOLVIMENTO-CAIXINHAS.md §4.5 — splits a distributed investment yield across vaults, in
 * proportion to each vault's <strong>settled</strong> ("current") balance on the day the yield is
 * booked, largest-remainder (Hamilton) apportionment:
 *
 * <pre>
 *   base_i  = the vault's settled balance on the yield's date; base_i &lt;= 0 is excluded entirely
 *   share_i = floor(total * base_i / Σbase)
 *   sobra   = total − Σ share_i           // 0 &lt;= sobra &lt; number of eligible vaults
 * </pre>
 *
 * <p><strong>Why the leftover goes to the largest remainder, and not to a fixed "last" vault like
 * {@link InstallmentSplitter} does:</strong> {@code InstallmentSplitter} hands its whole remainder
 * to the last installment because every installment is <em>meant</em> to be equal — "last" is an
 * arbitrary place to put a cent of imprecision that could have gone anywhere with the same fairness.
 * A yield's shares are not equal, they are <em>proportional</em>, so an arbitrary tie-breaker would
 * systematically favor whichever vault happens to sort last instead of the vault that is actually
 * owed the most by the rounding. Hamilton's method — give the spare cents one at a time to whichever
 * vault's {@code floor} truncated away the largest fraction — is the standard fair-division answer
 * to exactly this problem. <strong>Do not "simplify" this back to the last-part rule</strong>; that
 * would quietly make the split unfair, which is the whole reason this class exists instead of reusing
 * {@link InstallmentSplitter} directly.
 *
 * <p>Pure and static, in the same mold as {@link InstallmentSplitter}: no Spring, no database, no
 * clock — everything is decided by the two arguments, and {@link #distribute} asserts the one
 * invariant every caller relies on (the shares reconstruct the total, to the cent) before returning.
 * The {@code total * base} product is computed with {@link BigInteger} so a large yield split across
 * a vault holding a large balance can never silently overflow a {@code long}.
 */
public final class YieldDistributor {

    private YieldDistributor() {
    }

    /** One vault eligible to receive a share — its id, its name (for the tie-break) and its base. */
    public record VaultBase(UUID vaultId, String name, long baseCents) {
    }

    /** One vault's share of the yield — always strictly positive. */
    public record Share(UUID vaultId, long amountCents) {
    }

    /**
     * @param totalCents the yield being split, strictly positive
     * @param bases every candidate vault of the account; entries with {@code baseCents <= 0} are
     *     excluded from the split, not zeroed out — a vault that never held a positive settled
     *     balance simply never appears in the result
     * @return one {@link Share} per vault whose final share is strictly positive, summing to exactly
     *     {@code totalCents}. A vault whose proportional {@code floor} <em>and</em> the leftover-cent
     *     pass both land it on zero is dropped from the result rather than returned as a
     *     zero-amount share — {@code vault_entry}'s {@code amount_cents <> 0} check (V19) forbids
     *     writing one, and a share nobody actually received has nothing to record. <strong>Empty</strong>
     *     when no vault held a positive balance ({@code Σbase == 0}) — the caller is expected to let
     *     the whole yield land in the account's unallocated slice in that case, rather than inventing
     *     an allocation
     * @throws IllegalArgumentException {@code totalCents} is not strictly positive
     */
    public static List<Share> distribute(long totalCents, List<VaultBase> bases) {
        if (totalCents <= 0) {
            throw new IllegalArgumentException("totalCents must be positive, got " + totalCents);
        }

        List<VaultBase> eligible = bases.stream().filter(base -> base.baseCents() > 0).toList();
        BigInteger sum = BigInteger.ZERO;
        for (VaultBase base : eligible) {
            sum = sum.add(BigInteger.valueOf(base.baseCents()));
        }
        if (sum.signum() == 0) {
            // §4.5: no vault held a positive balance — honest to leave the yield unallocated rather
            // than invent a split.
            return List.of();
        }

        BigInteger total = BigInteger.valueOf(totalCents);
        List<Candidate> candidates = new ArrayList<>(eligible.size());
        long distributedCents = 0L;
        for (VaultBase base : eligible) {
            BigInteger[] quotientAndRemainder =
                    total.multiply(BigInteger.valueOf(base.baseCents())).divideAndRemainder(sum);
            long shareCents = quotientAndRemainder[0].longValueExact();
            distributedCents += shareCents;
            candidates.add(new Candidate(base, shareCents, quotientAndRemainder[1]));
        }

        // The leftover is always in [0, eligible.size()) — each floor() truncated away less than one
        // whole cent's worth of proportion, so there is at most one spare cent per candidate.
        long leftoverCents = totalCents - distributedCents;
        candidates.sort(Comparator.comparing(Candidate::remainder)
                .reversed()
                .thenComparing((Candidate candidate) -> candidate.base.baseCents(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.base.name().toLowerCase(Locale.ROOT))
                .thenComparing(candidate -> candidate.base.vaultId()));
        for (int i = 0; i < leftoverCents; i++) {
            candidates.get(i).shareCents += 1;
        }

        List<Share> shares = candidates.stream()
                .filter(candidate -> candidate.shareCents > 0)
                .map(candidate -> new Share(candidate.base.vaultId(), candidate.shareCents))
                .toList();
        assertSumMatches(shares, totalCents);
        return shares;
    }

    /** Mutable pairing of a candidate vault with the share it is accumulating, plus its remainder for sorting. */
    private static final class Candidate {
        private final VaultBase base;
        private long shareCents;
        private final BigInteger remainder;

        private Candidate(VaultBase base, long shareCents, BigInteger remainder) {
            this.base = base;
            this.shareCents = shareCents;
            this.remainder = remainder;
        }

        private BigInteger remainder() {
            return remainder;
        }
    }

    /** Guards the one invariant callers rely on: the shares reconstruct the total, to the cent. */
    private static void assertSumMatches(List<Share> shares, long totalCents) {
        long sum = 0;
        for (Share share : shares) {
            sum += share.amountCents();
        }
        if (sum != totalCents) {
            throw new IllegalStateException("Yield split lost cents: " + sum + " != " + totalCents);
        }
    }
}
