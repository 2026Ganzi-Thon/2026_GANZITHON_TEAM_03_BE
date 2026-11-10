package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.verification.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.verification.ip-max-per-minute=2")
@ActiveProfiles("mysql-integration")
@Import(EmailVerificationMySqlIntegrationTest.TestClockConfig.class)
class EmailVerificationMySqlIntegrationTest {
    @Autowired EmailVerificationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationTokens tokens;
    @Autowired MutableClock clock;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean VerificationMailSender mail;

    @DynamicPropertySource
    static void testDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void resetState() {
        reset(mail);
        jdbc.update("DELETE FROM auth_verifications");
        jdbc.update("DELETE FROM email_verification_limits");
        clock.now.set(Instant.parse("2026-11-10T10:00:00Z"));
    }

    private String send(String email) {
        service.send(email, "127.0.0.1");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void persistsOnlyHashAndIssuesPurposeBoundOneTimeSignupProof() {
        String raw = send("  Member@EXAMPLE.COM  ");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM auth_verifications", String.class)).isEqualTo(tokens.hash(raw));
        assertThat(jdbc.queryForObject("SELECT expires_at FROM auth_verifications", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 30));
        var proof = service.confirm(raw);
        assertThat(proof.email()).isEqualTo("member@example.com");
        assertThat(proof.expiresIn()).isEqualTo(600);
        assertCode(() -> service.confirm(raw), "AUTH_EMAIL_VERIFICATION_USED");
        assertCode(() -> service.confirm(proof.signupVerificationToken()), "AUTH_EMAIL_VERIFICATION_INVALID");
        var tx = new TransactionTemplate(transactionManager);
        assertCode(() -> tx.execute(status -> service.consumeSignupProof(proof.signupVerificationToken(), "other@example.com")), "AUTH_EMAIL_VERIFICATION_INVALID");
        Instant verified = tx.execute(status -> service.consumeSignupProof(proof.signupVerificationToken(), " MEMBER@EXAMPLE.COM "));
        assertThat(verified).isEqualTo(clock.instant());
        assertCode(() -> tx.execute(status -> service.consumeSignupProof(proof.signupVerificationToken(), proof.email())), "AUTH_EMAIL_VERIFICATION_USED");
    }

    @Test
    void enforcesExactExpiryAndSignupExpiry() {
        String raw = send("member@example.com");
        clock.advance(1800);
        assertCode(() -> service.confirm(raw), "AUTH_EMAIL_VERIFICATION_EXPIRED");
        String second = send("member@example.com");
        var proof = service.confirm(second);
        clock.advance(600);
        assertCode(() -> new TransactionTemplate(transactionManager).execute(status -> service.consumeSignupProof(
                proof.signupVerificationToken(), proof.email())), "AUTH_EMAIL_VERIFICATION_EXPIRED");
    }

    @Test
    void throttlesResendAndInvalidatesOldLinkOnlyAfterSuccessfulSend() {
        String old = send("member@example.com");
        assertCode(() -> service.send(" MEMBER@EXAMPLE.COM ", "127.0.0.2"), "AUTH_RATE_LIMITED");
        clock.advance(60);
        String next = send("member@example.com");
        assertCode(() -> service.confirm(old), "AUTH_EMAIL_VERIFICATION_USED");
        assertThat(service.confirm(next).email()).isEqualTo("member@example.com");
    }

    @Test
    void mailFailurePreservesPreviousLinkAndDoesNotClaimSuccess() {
        String old = send("member@example.com");
        clock.advance(60);
        doThrow(new VerificationException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_MAIL_UNAVAILABLE", "메일 발송 실패"))
                .when(mail).send(anyString(), anyString());
        assertCode(() -> service.send("member@example.com", "127.0.0.1"), "AUTH_MAIL_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_verifications", Integer.class)).isEqualTo(1);
        assertThat(service.confirm(old).email()).isEqualTo("member@example.com");
    }

    @Test
    void limitsIpEvenWhenDifferentEmailsAreUsedAndRejectsMalformedTokens() {
        send("one@example.com");
        send("two@example.com");
        assertCode(() -> service.send("three@example.com", "127.0.0.1"), "AUTH_RATE_LIMITED");
        assertCode(() -> service.confirm("invalid"), "AUTH_EMAIL_VERIFICATION_INVALID");
        assertCode(() -> service.confirm(tokens.issue()), "AUTH_EMAIL_VERIFICATION_INVALID");
    }

    @Test
    void parallelConfirmationsCreateExactlyOneSignupProof() throws Exception {
        String raw = send("member@example.com");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> confirm = () -> {
                start.await();
                try { service.confirm(raw); return true; }
                catch (VerificationException exception) {
                    assertThat(exception.getCode()).isEqualTo("AUTH_EMAIL_VERIFICATION_USED");
                    return false;
                }
            };
            var first = pool.submit(confirm);
            var second = pool.submit(confirm);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_verifications WHERE purpose='SIGNUP_VERIFICATION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void proofConsumptionRollsBackWithFailedSignup() {
        var proof = service.confirm(send("member@example.com"));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            service.consumeSignupProof(proof.signupVerificationToken(), proof.email());
            throw new IllegalStateException("signup failed");
        })).isInstanceOf(IllegalStateException.class);
        Instant verified = new TransactionTemplate(transactionManager).execute(status ->
                service.consumeSignupProof(proof.signupVerificationToken(), proof.email()));
        assertThat(verified).isNotNull();
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOf(VerificationException.class)
                .satisfies(error -> assertThat(((VerificationException) error).getCode()).isEqualTo(code));
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>();
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
}
