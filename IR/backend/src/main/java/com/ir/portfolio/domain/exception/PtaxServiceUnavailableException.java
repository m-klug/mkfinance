package com.ir.portfolio.domain.exception;

/** The BCB PTAX API could not be reached (timeout, network error or 5xx). */
public class PtaxServiceUnavailableException extends DomainException {

    public PtaxServiceUnavailableException(Throwable cause) {
        super(ErrorCode.PTAX_SERVICE_UNAVAILABLE,
                "Serviço PTAX do Banco Central indisponível no momento", cause);
    }
}
