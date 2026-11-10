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
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String frontendUrl;

    public SmtpVerificationMailSender(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.verification.from:}") String from,
            @Value("${app.verification.frontend-url:}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void send(String email, String token) {
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            URI url = URI.create(frontendUrl);
            boolean local = "localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost());
            if (sender == null || from.isBlank() || url.getHost() == null
                    || !("https".equals(url.getScheme()) || (local && "http".equals(url.getScheme())))
                    || url.getRawUserInfo() != null || url.getRawQuery() != null || url.getRawFragment() != null) {
                throw unavailable();
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("마음 돌봄 이메일 인증");
            // Fragment는 HTTP 요청·일반적인 프록시 접근 로그로 전송되지 않습니다.
            message.setText("30분 안에 아래 링크에서 이메일 인증을 완료해주세요.\n"
                    + frontendUrl + "#token=" + token
                    + "\n본인이 요청하지 않았다면 이 메일을 무시해주세요.");
            sender.send(message);
        } catch (VerificationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private VerificationException unavailable() {
        return new VerificationException(HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_MAIL_UNAVAILABLE", "인증 메일을 발송하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
}
