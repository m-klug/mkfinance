package br.com.mkfinance.domain.service;

import br.com.mkfinance.domain.model.Pillar;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The heart of BE-5: what share of the month's income each of the six pillars consumed
 * (PLANEJAMENTO §5 R2, R9, R10). Pure and static — sums in, report out, no Spring and no database —
 * so every rule below is a property of a single function the unit tests pin down by hand.
 *
 * <p>The three rules it encodes:
 *
 * <ul>
 *   <li><strong>R10 (competency):</strong> a pillar's percent is {@code spent ÷ month income × 100}.
 *       "Spent" is measured by competency, which is the caller's job — a card purchase must arrive
 *       here counted on its purchase date, never on the date its statement was paid. This function
 *       only divides.
 *   <li><strong>R2 (no double counting):</strong> the debit that pays a credit-card statement is an
 *       EXPENSE under a {@code system = true} category. Its spending was already counted on the
 *       purchase date, so an {@link ExpenseEntry} flagged {@code systemCategory} is dropped here —
 *       otherwise the whole card would be counted twice, once per purchase and once at payment.
 *   <li><strong>R9 (contributions):</strong> an aporte into an investment/savings account is not an
 *       expense (it is a transfer, R4), but it must still consume its pillar (GOALS or
 *       FINANCIAL_FREEDOM) or those two pillars would never fill. Contributions are added to their
 *       pillar's spent total alongside expenses.
 * </ul>
 *
 * <p>Transfers that are not contributions never reach this function at all (R4) — the caller filters
 * them out of both income and expense.
 */
public final class PillarAllocationCalculator {

    private PillarAllocationCalculator() {
    }

    /**
     * One competency expense already attributed to a pillar. {@code systemCategory} marks the
     * statement-payment debit (R2), which is excluded so the card is not counted twice.
     */
    public record ExpenseEntry(Pillar pillar, long amountCents, boolean systemCategory) {
    }

    /** One contribution (aporte, R9) directed at a pillar — GOALS or FINANCIAL_FREEDOM. */
    public record ContributionEntry(Pillar pillar, long amountCents) {
    }

    /**
     * A pillar's line in the report.
     *
     * @param actualPercent {@code spent ÷ income × 100}, or {@code null} when the month had no income
     *     — the client then shows {@code spentCents} as an absolute value (R10).
     */
    public record PillarLine(Pillar pillar, int targetPercent, long spentCents, Double actualPercent) {
    }

    /**
     * The month's allocation report.
     *
     * @param noIncome true when income is zero: percents are null and the absolute {@code spentCents}
     *     should be shown instead, since dividing by zero would distort the whole method (R10)
     */
    public record Report(long incomeCents, boolean noIncome, List<PillarLine> pillars) {
    }

    /**
     * Builds the report for a month.
     *
     * @param incomeCents the month's income (INCOME transactions, transfers excluded — R4)
     * @param expenses competency expenses attributed to pillars; system-category ones are dropped (R2)
     * @param contributions aportes, added to their pillar (R9)
     * @param targetPercents the fixed target per pillar; a missing pillar defaults to 0
     */
    public static Report allocate(
            long incomeCents,
            Collection<ExpenseEntry> expenses,
            Collection<ContributionEntry> contributions,
            Map<Pillar, Integer> targetPercents) {

        EnumMap<Pillar, Long> spentByPillar = new EnumMap<>(Pillar.class);
        for (Pillar pillar : Pillar.values()) {
            spentByPillar.put(pillar, 0L);
        }

        for (ExpenseEntry expense : expenses) {
            if (expense.systemCategory() || expense.pillar() == null) {
                continue; // R2: the statement-payment debit was already counted on the purchase date.
            }
            spentByPillar.merge(expense.pillar(), expense.amountCents(), Long::sum);
        }
        for (ContributionEntry contribution : contributions) {
            if (contribution.pillar() == null) {
                continue;
            }
            spentByPillar.merge(contribution.pillar(), contribution.amountCents(), Long::sum); // R9
        }

        boolean noIncome = incomeCents <= 0;
        List<PillarLine> lines = new ArrayList<>(Pillar.values().length);
        for (Pillar pillar : Pillar.values()) {
            long spent = spentByPillar.get(pillar);
            int target = targetPercents.getOrDefault(pillar, 0);
            Double actualPercent = noIncome ? null : (spent * 100.0) / incomeCents; // R10
            lines.add(new PillarLine(pillar, target, spent, actualPercent));
        }
        return new Report(incomeCents, noIncome, lines);
    }
}
