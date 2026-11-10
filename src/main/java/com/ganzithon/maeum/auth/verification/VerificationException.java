package com.ganzithon.maeum.auth.verification;

import org.springframework.http.HttpStatus;

public class VerificationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public VerificationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
