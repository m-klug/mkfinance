package com.ir.portfolio.domain.exception;

import java.time.LocalDate;

/** No PTAX quote exists in the search window (weekend/holiday run or future date). */
public class PtaxUnavailableException extends DomainException {

    public PtaxUnavailableException(LocalDate requestedDate, int maxForwardDays) {
        super(ErrorCode.PTAX_UNAVAILABLE,
                "Nenhuma cotação PTAX disponível para %s (busca até D+%d)".formatted(requestedDate, maxForwardDays));
    }
}
