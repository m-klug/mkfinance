package com.ir.portfolio.api.error;

import com.ir.portfolio.domain.exception.BusinessRuleException;
import com.ir.portfolio.domain.exception.ErrorCode;
import com.ir.portfolio.domain.exception.PtaxServiceUnavailableException;
import com.ir.portfolio.domain.exception.PtaxUnavailableException;
import com.ir.portfolio.domain.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Extends {@link ResponseEntityExceptionHandler} so framework exceptions
 * (validation, missing params, type mismatches...) keep their RFC 7807
 * rendering while custom extensions ({@code code}, {@code errors}) are added.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        HttpStatus status = exception.getCode() == ErrorCode.TICKER_ALREADY_EXISTS
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        ProblemDetail problem = problem(status, exception.getCode(), exception.getMessage());
        exception.getDetails().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(PtaxUnavailableException.class)
    ProblemDetail handlePtaxUnavailable(PtaxUnavailableException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(PtaxServiceUnavailableException.class)
    ProblemDetail handlePtaxServiceUnavailable(PtaxServiceUnavailableException exception) {
        log.warn("BCB PTAX service unavailable", exception);
        return problem(HttpStatus.BAD_GATEWAY, exception.getCode(), exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException exception, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Requisição inválida");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }
}
