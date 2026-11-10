package com.ganzithon.maeum.global.api;

import com.ganzithon.maeum.auth.verification.VerificationException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> unsupported(Exception exception) {
        var status = ((org.springframework.web.ErrorResponse) exception).getStatusCode();
        return ResponseEntity.status(status).body(new ApiError(
                "COMMON_INVALID_REQUEST", "지원하지 않는 요청 방식입니다.", List.of()));
    }

    @ExceptionHandler(VerificationException.class)
    ResponseEntity<ApiError> verification(VerificationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
                new ApiError(exception.getCode(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), "입력값을 확인해주세요."))
                .distinct().toList();
        return ResponseEntity.badRequest().body(new ApiError(
                "COMMON_INVALID_REQUEST", "입력값을 확인해주세요.", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "COMMON_INVALID_REQUEST", "요청 형식을 확인해주세요.", List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        // 메일 주소·원문 토큰·SMTP 자격증명이 예외 메시지로 노출되지 않게 합니다.
        return ResponseEntity.internalServerError().body(new ApiError(
                "COMMON_INTERNAL_ERROR", "요청을 처리하지 못했습니다.", List.of()));
    }
}
