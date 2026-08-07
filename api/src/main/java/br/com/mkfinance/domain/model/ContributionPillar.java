package br.com.mkfinance.domain.model;

/**
 * Subset of {@link Pillar} an account/transfer can direct a contribution to (R9 — aporte).
 * Kept as a distinct type (mirrors the {@code ContributionPillar} schema in the OpenAPI
 * contract) so invalid values (e.g. FIXED_COSTS) are rejected at JSON deserialization time
 * instead of needing manual validation everywhere.
 *
 * <p>What makes a pillar contributable is that its money is <em>set aside before it is spent</em>:
 * GOALS and FINANCIAL_FREEDOM were always like that, and KNOWLEDGE (V20) joined them because study
 * money behaves the same way — put aside month after month, spent on a course later. The pillars
 * left out (FIXED_COSTS, COMFORT, PLEASURES) are consumed as they are spent, so parking money for
 * them would only mean counting the same money twice.
 */
public enum ContributionPillar {
    GOALS,
    FINANCIAL_FREEDOM,
    KNOWLEDGE;

    public Pillar toPillar() {
        return Pillar.valueOf(name());
    }

    /**
     * Whether a {@link Pillar} is one an aporte can consume. Asked instead of listing the members by
     * hand, so a pillar added to this enum is admitted everywhere at once — the one thing that has
     * to keep agreeing with it is the schema's CHECK constraints.
     */
    public static boolean accepts(Pillar pillar) {
        if (pillar == null) {
            return false;
        }
        for (ContributionPillar candidate : values()) {
            if (candidate.name().equals(pillar.name())) {
                return true;
            }
        }
        return false;
    }

    public static ContributionPillar fromPillar(Pillar pillar) {
        if (pillar == null) {
            return null;
        }
        return switch (pillar) {
            case GOALS -> GOALS;
            case FINANCIAL_FREEDOM -> FINANCIAL_FREEDOM;
            case KNOWLEDGE -> KNOWLEDGE;
            default -> throw new IllegalArgumentException("Not a contribution pillar: " + pillar);
        };
    }
}
