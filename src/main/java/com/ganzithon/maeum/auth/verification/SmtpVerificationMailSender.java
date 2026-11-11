package com.ganzithon.maeum.auth.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final SmtpAuthMail mail;
    private final String frontendUrl;

    public SmtpVerificationMailSender(SmtpAuthMail mail,
            @Value("${app.verification.frontend-url:}") String frontendUrl) {
        this.mail = mail;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void send(String email, String token) {
        mail.sendLink(email, token, frontendUrl, "마음 돌봄 이메일 인증",
                "30분 안에 아래 링크에서 이메일 인증을 완료해주세요.");
    }
}
