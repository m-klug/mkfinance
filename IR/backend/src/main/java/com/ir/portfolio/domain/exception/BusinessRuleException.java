package com.ir.portfolio.domain.exception;

import java.util.Map;

public class BusinessRuleException extends DomainException {

    private final transient Map<String, Object> details;

    public BusinessRuleException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BusinessRuleException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message);
        this.details = details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
