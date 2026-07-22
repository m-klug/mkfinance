package br.com.mkfinance.domain.model;

/**
 * Subset of {@link Pillar} an account/transfer can direct a contribution to (R9 — aporte).
 * Kept as a distinct type (mirrors the {@code ContributionPillar} schema in the OpenAPI
 * contract) so invalid values (e.g. FIXED_COSTS) are rejected at JSON deserialization time
 * instead of needing manual validation everywhere.
 */
public enum ContributionPillar {
    GOALS,
    FINANCIAL_FREEDOM;

    public Pillar toPillar() {
        return Pillar.valueOf(name());
    }

    public static ContributionPillar fromPillar(Pillar pillar) {
        if (pillar == null) {
            return null;
        }
        return switch (pillar) {
            case GOALS -> GOALS;
            case FINANCIAL_FREEDOM -> FINANCIAL_FREEDOM;
            default -> throw new IllegalArgumentException("Not a contribution pillar: " + pillar);
        };
    }
}
