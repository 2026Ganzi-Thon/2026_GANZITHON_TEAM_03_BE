package com.ganzithon.maeum.user.profile;

import com.ganzithon.maeum.global.api.ApiError;
import java.util.List;
import org.springframework.http.HttpStatus;

final class ProfileException extends RuntimeException {
    private final HttpStatus status;
    private final ApiError error;

    ProfileException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    private ProfileException(HttpStatus status, String code, String message, List<ApiError.FieldError> fields) {
        super(message);
        this.status = status;
        this.error = new ApiError(code, message, fields);
    }

    static ProfileException invalid(String field, String message) {
        return new ProfileException(HttpStatus.BAD_REQUEST, "COMMON_INVALID_REQUEST", message,
                List.of(new ApiError.FieldError(field, message)));
    }

    HttpStatus status() { return status; }

    ApiError error() { return error; }
}
