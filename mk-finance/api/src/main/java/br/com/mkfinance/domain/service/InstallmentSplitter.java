package br.com.mkfinance.domain.service;

/**
 * PLANEJAMENTO §5 R1 — splits a purchase into installments using integer cents only.
 *
 * <p>The remainder of the division always goes to the <strong>last</strong> installment, so the
 * parts add back up to the total exactly: R$ 100,00 in 3 → 33,33 + 33,33 + 33,34. Splitting in
 * {@code double} (or rounding each part independently) is what makes other apps show a purchase
 * whose installments sum to one cent more or less than what was charged; here that is impossible by
 * construction, and {@link #split} asserts it before returning.
 *
 * <p>Pure and static: no Spring, no database, no clock — everything this class does is decided by
 * its two arguments, which is what makes the rule cheap to test exhaustively.
 */
public final class InstallmentSplitter {

    private InstallmentSplitter() {
    }

    /**
     * Splits {@code totalCents} into {@code count} parts, remainder on the last one.
     *
     * <p>When the total is smaller than the number of installments the leading parts come out as
     * zero (2 cents in 3 → 0, 0, 2) rather than failing: the sum still matches the total, which is
     * the invariant that matters, and the {@code installment.amount_cents >= 0} check in
     * V1__schema.sql accepts it.
     *
     * @param totalCents the purchase total in cents, strictly positive
     * @param count how many installments, at least 1
     * @return an array of {@code count} amounts in cents whose sum is exactly {@code totalCents}
     */
    public static long[] split(long totalCents, int count) {
        if (totalCents <= 0) {
            throw new IllegalArgumentException("totalCents must be positive, got " + totalCents);
        }
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1, got " + count);
        }

        long base = totalCents / count;
        long[] parts = new long[count];
        java.util.Arrays.fill(parts, base);
        // Everything the equal split could not distribute lands on the final installment.
        parts[count - 1] = totalCents - base * (count - 1);

        assertSumMatches(parts, totalCents);
        return parts;
    }

    /** Guards the one invariant callers rely on: the parts reconstruct the total, to the cent. */
    private static void assertSumMatches(long[] parts, long totalCents) {
        long sum = 0;
        for (long part : parts) {
            sum += part;
        }
        if (sum != totalCents) {
            throw new IllegalStateException("Split lost cents: " + sum + " != " + totalCents);
        }
    }
}
