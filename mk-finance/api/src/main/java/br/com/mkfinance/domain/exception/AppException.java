package br.com.mkfinance.domain.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Single exception type for business-rule / not-found / auth failures translated by
 * {@code br.com.mkfinance.api.error.GlobalExceptionHandler} into the contract's
 * {@code ErrorResponse} shape ({@code code} + optional {@code params}). Never carries a
 * human-readable / localized message — {@link #getCode()} is the only thing the client reads.
 */
@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> params;

    private AppException(String code, HttpStatus status, Map<String, Object> params) {
        super(code);
        this.code = code;
        this.status = status;
        this.params = params;
    }

    public static AppException of(String code, HttpStatus status) {
        return new AppException(code, status, Map.of());
    }

    public static AppException of(String code, HttpStatus status, Map<String, Object> params) {
        return new AppException(code, status, params);
    }

    public static AppException emailAlreadyUsed(String email) {
        return of(ErrorCode.EMAIL_ALREADY_USED, HttpStatus.CONFLICT, Map.of("email", email));
    }

    public static AppException invalidCredentials() {
        return of(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }

    public static AppException notAMember() {
        return of(ErrorCode.NOT_A_MEMBER, HttpStatus.FORBIDDEN);
    }

    public static AppException inviteExpired() {
        return of(ErrorCode.INVITE_EXPIRED, HttpStatus.GONE);
    }

    public static AppException accountNotFound() {
        return of(ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException accountHasTransactions() {
        return of(ErrorCode.ACCOUNT_HAS_TRANSACTIONS, HttpStatus.CONFLICT);
    }

    public static AppException invalidContributionPillar() {
        return of(ErrorCode.INVALID_CONTRIBUTION_PILLAR, HttpStatus.BAD_REQUEST);
    }

    public static AppException categoryNotFound() {
        return of(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    public static AppException categoryInUse() {
        return of(ErrorCode.CATEGORY_IN_USE, HttpStatus.CONFLICT);
    }

    public static AppException categoryPillarRequired() {
        return of(ErrorCode.CATEGORY_PILLAR_REQUIRED, HttpStatus.BAD_REQUEST);
    }

    public static AppException categoryPillarForbidden() {
        return of(ErrorCode.CATEGORY_PILLAR_FORBIDDEN, HttpStatus.BAD_REQUEST);
    }

    public static AppException systemCategoryReadOnly() {
        return of(ErrorCode.SYSTEM_CATEGORY_READONLY, HttpStatus.CONFLICT);
    }
}
