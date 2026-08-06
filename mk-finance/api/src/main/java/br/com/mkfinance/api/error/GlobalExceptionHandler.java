package br.com.mkfinance.api.error;

import br.com.mkfinance.application.recurrence.RecurrenceValidationException;
import br.com.mkfinance.application.vault.VaultValidationException;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates every exception into the contract's {@code ErrorResponse} shape
 * ({@code code} + optional {@code params}/{@code fieldErrors}) — machine codes only, never a
 * localized message (PLANEJAMENTO §3.3).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        Map<String, Object> params = ex.getParams();
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getCode(), params == null || params.isEmpty() ? null : params, null));
    }

    /** Thrown by AuthenticationManager.authenticate(...) on bad credentials (login endpoint). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ErrorCode.INVALID_CREDENTIALS, null, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDto(fe.getField(), toMachineCode(fe.getCode())))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorCode.VALIDATION_ERROR, null, fieldErrors));
    }

    /**
     * The conditional scheduling validation OpenAPI cannot express (CT-1: which field a
     * {@code RecurrenceRequest} requires depends on {@code frequency}) — reported the same way Bean
     * Validation would be.
     */
    @ExceptionHandler(RecurrenceValidationException.class)
    public ResponseEntity<ErrorResponse> handleRecurrenceValidation(RecurrenceValidationException ex) {
        List<FieldErrorDto> fieldErrors = ex.getViolations().stream()
                .map(violation -> new FieldErrorDto(violation.field(), violation.code()))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorCode.VALIDATION_ERROR, null, fieldErrors));
    }

    /**
     * The vault validations OpenAPI cannot express (CT-2: {@code targetDate} without
     * {@code targetAmountCents}; a reallocation with both {@code fromVaultId}/{@code toVaultId}
     * null) — reported the same way Bean Validation would.
     */
    @ExceptionHandler(VaultValidationException.class)
    public ResponseEntity<ErrorResponse> handleVaultValidation(VaultValidationException ex) {
        List<FieldErrorDto> fieldErrors = ex.getViolations().stream()
                .map(violation -> new FieldErrorDto(violation.field(), violation.code()))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorCode.VALIDATION_ERROR, null, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorCode.VALIDATION_ERROR, null, null));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ErrorCode.VALIDATION_ERROR, null, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ErrorCode.DATA_INTEGRITY_VIOLATION, null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse(ErrorCode.INTERNAL_ERROR, null, null));
    }

    private String toMachineCode(String springValidationCode) {
        if (springValidationCode == null) {
            return "INVALID";
        }
        return springValidationCode.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }
}
