package com.ganzithon.maeum.auth.verification;

public interface VerificationMailSender {
    void send(String email, String token);
}
