package br.com.mkfinance.domain.model;

/**
 * PLANEJAMENTO §7 (Fase 2) — how often a {@link RecurrenceRule} repeats. MONTHLY is the case that
 * matters (salary, rent, subscriptions); WEEKLY and YEARLY round it out. Deliberately not a cron
 * expression — nothing in a personal budget needs one.
 */
public enum RecurrenceFrequency {
    MONTHLY,
    WEEKLY,
    YEARLY
}
