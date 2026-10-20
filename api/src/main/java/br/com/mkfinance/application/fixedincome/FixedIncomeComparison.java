package br.com.mkfinance.application.fixedincome;

/**
 * Which of two simulations yields more, net.
 *
 * <p>Decided by the net annual equivalent rate, not by the net amount: it is what stays comparable
 * when the two differ in term or in how much is applied. When both are the same size and length the
 * two criteria agree.
 *
 * @param betterIndex 0 or 1, the position of the winner in the request; null when they tie (the
 *     equivalent rates match to two decimals)
 * @param netDifferenceCents absolute difference between the two net amounts
 * @param netAnnualEquivalentDifferencePercent absolute difference between the two equivalent rates,
 *     in percentage points
 */
public record FixedIncomeComparison(
        Integer betterIndex, long netDifferenceCents, double netAnnualEquivalentDifferencePercent) {
}
