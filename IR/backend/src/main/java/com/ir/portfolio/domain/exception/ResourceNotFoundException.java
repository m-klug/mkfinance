package com.ir.portfolio.domain.exception;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
