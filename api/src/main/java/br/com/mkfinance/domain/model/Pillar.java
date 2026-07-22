package br.com.mkfinance.domain.model;

/**
 * The 6 fixed allocation pillars (PLANEJAMENTO §3.3/§5 — método dos 6 pilares). Fixed,
 * not editable nor deletable; every EXPENSE category belongs to exactly one.
 */
public enum Pillar {
    FIXED_COSTS,
    COMFORT,
    GOALS,
    PLEASURES,
    FINANCIAL_FREEDOM,
    KNOWLEDGE
}
