package br.com.mkfinance.api.error;

import java.util.List;
import java.util.Map;

/** Matches {@code components.schemas.ErrorResponse} in contracts/openapi.yaml exactly. */
public record ErrorResponse(String code, Map<String, Object> params, List<FieldErrorDto> fieldErrors) {
}
