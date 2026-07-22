package com.ir.portfolio.domain.port;

import com.ir.portfolio.domain.model.PtaxQuote;
import com.ir.portfolio.domain.model.PtaxRateType;

import java.time.LocalDate;

/** Outbound port for resolving PTAX exchange rates. */
public interface PtaxProvider {

    /**
     * Resolves the PTAX rate for the requested date, walking forward to the
     * next business day when necessary (weekend/holiday fallback).
     */
    PtaxQuote get(LocalDate requestedDate, PtaxRateType type);
}
