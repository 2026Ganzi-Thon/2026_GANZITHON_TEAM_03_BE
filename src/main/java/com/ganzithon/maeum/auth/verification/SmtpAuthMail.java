package com.ganzithon.maeum.auth.verification;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!bootstrap")
public class SmtpAuthMail {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpAuthMail(ObjectProvider<JavaMailSender> mailSender, @Value("${app.verification.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void checkAvailable(String frontendUrl) {
        try {
            URI url = URI.create(frontendUrl);
            boolean local = "localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost());
            if (mailSender.getIfAvailable() == null || from.isBlank() || url.getHost() == null
                    || !("https".equals(url.getScheme()) || (local && "http".equals(url.getScheme())))
                    || url.getRawUserInfo() != null || url.getRawQuery() != null || url.getRawFragment() != null) {
                throw unavailable();
            }
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public void sendLink(String email, String token, String frontendUrl, String subject, String instruction) {
        checkAvailable(frontendUrl);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(instruction + "\n" + frontendUrl + "#token=" + token
                    + "\n본인이 요청하지 않았다면 이 메일을 무시해주세요.");
            mailSender.getObject().send(message);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public static VerificationException unavailable() {
        return new VerificationException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_MAIL_UNAVAILABLE",
                "인증 메일을 발송하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
}
