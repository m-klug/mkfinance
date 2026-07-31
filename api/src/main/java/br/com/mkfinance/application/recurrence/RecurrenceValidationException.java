package br.com.mkfinance.application.recurrence;

import java.util.List;
import lombok.Getter;

/**
 * The scheduling fields OpenAPI cannot make conditionally required — {@code dayOfMonth} for
 * MONTHLY, {@code dayOfWeek} for WEEKLY, {@code dayOfMonth} + {@code monthOfYear} for YEARLY — plus
 * the {@code occurrenceDate} pivot {@code THIS_ONE} needs (CT-1). {@code domain/exception} already
 * owns {@code AppException} and every code this feature needs (VALIDATION_ERROR included), so this
 * type only carries the field-level detail; {@code GlobalExceptionHandler} reports it exactly like
 * Bean Validation would — VALIDATION_ERROR with {@code fieldErrors}.
 */
@Getter
public class RecurrenceValidationException extends RuntimeException {

    public record FieldViolation(String field, String code) {
    }

    private final List<FieldViolation> violations;

    public RecurrenceValidationException(List<FieldViolation> violations) {
        super("Invalid recurrence request: " + violations);
        this.violations = violations;
    }

    public static RecurrenceValidationException of(String field, String code) {
        return new RecurrenceValidationException(List.of(new FieldViolation(field, code)));
    }
}
