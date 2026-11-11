package com.ganzithon.maeum.auth.session;

import com.ganzithon.maeum.auth.verification.VerificationException;
import org.springframework.http.HttpStatus;

public class AuthFault extends VerificationException {
    public AuthFault(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
