package br.com.mkfinance.application.vault;

import java.util.List;
import lombok.Getter;

/**
 * The vault fields OpenAPI cannot make conditionally required — {@code targetDate} without
 * {@code targetAmountCents} on {@code VaultRequest}, and a {@code VaultReallocationRequest} whose
 * {@code fromVaultId}/{@code toVaultId} are both null (DESENVOLVIMENTO-CAIXINHAS.md §4.7's "both
 * null is 400 VALIDATION_ERROR + fieldErrors"). Same shape and same reason as
 * {@code application.recurrence.RecurrenceValidationException}: {@code domain/exception} already
 * owns {@code AppException} and VALIDATION_ERROR, so this type only carries the field-level detail
 * and {@code GlobalExceptionHandler} reports it exactly like Bean Validation would.
 */
@Getter
public class VaultValidationException extends RuntimeException {

    public record FieldViolation(String field, String code) {
    }

    private final List<FieldViolation> violations;

    public VaultValidationException(List<FieldViolation> violations) {
        super("Invalid vault request: " + violations);
        this.violations = violations;
    }

    public static VaultValidationException of(String field, String code) {
        return new VaultValidationException(List.of(new FieldViolation(field, code)));
    }
}
