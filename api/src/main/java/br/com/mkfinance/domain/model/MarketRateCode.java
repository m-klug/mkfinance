package br.com.mkfinance.domain.model;

/** The market rates the fixed-income simulator needs, each with the unit its value is stored in. */
public enum MarketRateCode {
    /** CDI, % a.a. (base 252). */
    CDI,
    /** Selic target set by the Copom, % a.a. */
    SELIC,
    /** IPCA accumulated over 12 months, %; only a suggestion for the simulator's projection. */
    IPCA
}
