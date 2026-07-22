package com.ir.portfolio.api.dto;

import com.ir.portfolio.domain.model.PtaxRateType;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PtaxDtos {

    private PtaxDtos() {
    }

    /** Full response of GET /ptax. */
    public record PtaxResponse(
            BigDecimal rate,
            PtaxRateType type,
            LocalDate quoteDate,
            LocalDate requestedDate,
            boolean fallback
    ) {
    }

    /** Trimmed PTAX info embedded in order/ledger lines (mirrors the front-end shape). */
    public record PtaxInfo(
            BigDecimal rate,
            PtaxRateType type,
            LocalDate quoteDate,
            boolean fallback
    ) {
    }
}
