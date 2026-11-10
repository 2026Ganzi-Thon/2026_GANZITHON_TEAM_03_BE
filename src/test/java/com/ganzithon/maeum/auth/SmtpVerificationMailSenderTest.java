package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.verification.SmtpVerificationMailSender;
import com.ganzithon.maeum.auth.verification.VerificationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpVerificationMailSenderTest {
    @Test
    void sendsLinkToConfiguredFrontendAndNeverUsesRequestHost() {
        var mail = mock(JavaMailSender.class);
        var sender = sender(mail, "https://demo.example.com/verify-email");
        sender.send("member@example.com", "test-token");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("member@example.com");
        assertThat(message.getValue().getText()).contains("https://demo.example.com/verify-email#token=test-token");
    }

    @Test
    void missingSettingsAndSmtpFailureAre503WithoutSecretDetails() {
        var factory = new DefaultListableBeanFactory();
        var missing = new SmtpVerificationMailSender(factory.getBeanProvider(JavaMailSender.class), "", "");
        assertThatThrownBy(() -> missing.send("member@example.com", "token"))
                .isInstanceOf(VerificationException.class).hasMessageNotContaining("token");
        var mail = mock(JavaMailSender.class);
        doThrow(new MailSendException("sensitive-internal-detail")).when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> sender(mail, "https://demo.example.com/verify").send("member@example.com", "token"))
                .isInstanceOf(VerificationException.class).hasMessageNotContaining("sensitive-internal-detail");
    }

    @Test
    void remotePlainHttpLinkIsRejected() {
        var mail = mock(JavaMailSender.class);
        assertThatThrownBy(() -> sender(mail, "http://demo.example.com/verify").send("member@example.com", "token"))
                .isInstanceOf(VerificationException.class);
        verifyNoInteractions(mail);
    }

    private SmtpVerificationMailSender sender(JavaMailSender mail, String frontend) {
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("mail", mail);
        return new SmtpVerificationMailSender(factory.getBeanProvider(JavaMailSender.class), "sender@example.com", frontend);
    }
}
