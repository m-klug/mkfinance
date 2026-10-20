package br.com.mkfinance.domain.model;

/** The post-fixed indexes whose daily history is kept. */
public enum IndexCode {
    /** CDI, the day's rate in % per day. */
    CDI,
    /** Selic effective rate of the day, in % per day (not the Copom target). */
    SELIC
}
