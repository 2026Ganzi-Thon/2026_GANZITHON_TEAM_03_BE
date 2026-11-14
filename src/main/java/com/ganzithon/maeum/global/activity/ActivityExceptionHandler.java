package com.ganzithon.maeum.global.activity;

import com.ganzithon.maeum.global.api.ApiError;

import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice(
        basePackages = {
            "com.ganzithon.maeum.emotion",
            "com.ganzithon.maeum.care",
            "com.ganzithon.maeum.concern",
            "com.ganzithon.maeum.rest"
        })
@Order(0)
class ActivityExceptionHandler {
    @ExceptionHandler(ActivityApi.Problem.class)
    ResponseEntity<ApiError> domain(ActivityApi.Problem problem) {
        return response(problem.status, problem.code, problem.getMessage());
    }

    @ExceptionHandler({
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> invalidRequest() {
        return response(400, "COMMON_INVALID_REQUEST", "입력값을 확인해주세요.");
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    ResponseEntity<ApiError> retryableConflict() {
        return response(409, "COMMON_STATE_CONFLICT", "현재 상태와 충돌하는 요청입니다.");
    }

    private ResponseEntity<ApiError> response(int status, String code, String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(code, message, List.of()));
    }
}
