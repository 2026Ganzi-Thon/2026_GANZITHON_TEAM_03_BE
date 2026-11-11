package com.ganzithon.maeum.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.session.AuthCookies;
import com.ganzithon.maeum.auth.session.AuthSessionService;
import com.ganzithon.maeum.auth.verification.VerificationTokens;
import com.ganzithon.maeum.user.domain.User;
import com.ganzithon.maeum.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.auth.jwt-secret-base64=dGVzdC1maXh0dXJlLW9ubHktMzItYnl0ZS1zaWduaW5nLWtleQ==",
        "app.auth.google-client-id=test-client",
        "app.auth.allowed-origins=https://frontend.example.com",
        "app.auth.terms-version=v1",
        "app.auth.privacy-version=v1",
        "app.auth.marketing-version=v1",
        "app.verification.from=test@example.com",
        "app.verification.password-reset-frontend-url=https://frontend.example.com/reset-password"
})
@ActiveProfiles({"mysql-integration", "auth"})
@AutoConfigureMockMvc
@Import(PasswordResetMySqlIntegrationTest.ClockConfig.class)
class PasswordResetMySqlIntegrationTest {
    private static final String PATH = "/api/v1/auth/password-resets";
    private static final String EMAIL = "member@example.com";
    private static final String OLD = "OldPass1!";
    private static final String NEW = " NewPass2! ";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalMemberStore members;
    @Autowired UserRepository users;
    @Autowired VerificationTokens tokens;
    @Autowired AuthSessionService sessions;
    @Autowired TransactionTemplate transactions;
    @Autowired MutableClock clock;
    @MockitoBean JavaMailSender mail;
    @MockitoBean(name = "passwordResetExecutor") ThreadPoolTaskExecutor executor;
    private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void resetState() {
        for (String table : List.of("refresh_tokens", "auth_sessions", "user_consents", "social_accounts",
                "local_credentials", "auth_verifications", "email_verification_limits", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        clock.now = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        jobs.clear();
        doAnswer(call -> {
            jobs.add(call.getArgument(0, Runnable.class));
            return null;
        }).when(executor).execute(any(Runnable.class));
    }

    @Test
    void resetChangesPasswordAndRevokesEverySessionForThatMember() throws Exception {
        long id = member();
        var other = transactions.execute(status -> sessions.create(users.save(new User("other@example.com")).getId()));
        var first = transactions.execute(status -> sessions.create(id));
        var second = transactions.execute(status -> sessions.create(id));
        String raw = issue();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM auth_verifications WHERE user_id=?",
                String.class, id)).isEqualTo(tokens.hash(raw));

        apply(raw, "weak").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_POLICY_VIOLATION"));
        apply(raw, NEW).andExpect(status().isNoContent()).andExpect(content().string(""));
        apply(raw, NEW).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_USED"));

        for (var session : List.of(first, second)) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + session.accessToken()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://frontend.example.com")
                    .cookie(new Cookie(AuthCookies.REFRESH, session.refreshToken())))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk());
        login(OLD).andExpect(status().isUnauthorized());
        login(NEW).andExpect(status().isOk());
    }

    @Test
    void knownUnknownAndSocialAccountsReceiveTheSameAcceptance() throws Exception {
        member();
        var social = users.saveAndFlush(new User("social@example.com"));
        jdbc.update("INSERT INTO social_accounts (user_id, provider, provider_user_id, created_at) VALUES (?, 'GOOGLE', ?, UTC_TIMESTAMP(6))",
                social.getId(), "test-reset-social");
        String expected = request(" MEMBER@EXAMPLE.COM ").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        for (String email : List.of("unknown@example.com", "social@example.com")) {
            request(email).andExpect(status().isAccepted()).andExpect(content().json(expected));
        }
        verifyNoInteractions(mail);
        jobs.forEach(Runnable::run);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(EMAIL);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_verifications", Integer.class)).isEqualTo(1);
    }

    @Test
    void resendIsThrottledAndFailedDeliveryKeepsPreviousLink() throws Exception {
        member();
        String first = issue();
        request(EMAIL).andExpect(status().isTooManyRequests());
        clock.now = clock.now.plusSeconds(60);
        doThrow(new MailSendException("test SMTP failure")).when(mail).send(any(SimpleMailMessage.class));
        request(EMAIL).andExpect(status().isAccepted());
        jobs.removeFirst().run();
        apply(first, NEW).andExpect(status().isNoContent());
    }

    @Test
    void expiredAndWrongPurposeTokensAreRejected() throws Exception {
        member();
        String raw = issue();
        clock.now = clock.now.plusSeconds(1800);
        apply(raw, NEW).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_EXPIRED"));

        String signup = tokens.issue();
        jdbc.update("""
                INSERT INTO auth_verifications (email, purpose, token_hash, expires_at, created_at)
                VALUES (?, 'SIGNUP_VERIFICATION', ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY), UTC_TIMESTAMP(6))
                """, EMAIL, tokens.hash(signup));
        apply(signup, NEW).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_RESET_INVALID"));
    }

    @Test
    void resendInvalidatesPriorTokenAndParallelResetsSpendTheLatestOnce() throws Exception {
        member();
        String prior = issue();
        clock.now = clock.now.plusSeconds(60);
        clearInvocations(mail);
        String latest = issue();
        apply(prior, NEW).andExpect(status().isConflict());

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> apply(latest, NEW).andReturn().getResponse().getStatus());
            var second = pool.submit(() -> apply(latest, NEW).andReturn().getResponse().getStatus());
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 409);
        }
    }

    private long member() {
        return members.createVerified(EMAIL, OLD, clock.instant()).getId();
    }

    private String issue() throws Exception {
        request(EMAIL).andExpect(status().isAccepted());
        jobs.removeFirst().run();
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        String link = sent.getValue().getText().lines().filter(line -> line.contains("#token=")).findFirst().orElseThrow();
        assertThat(link).startsWith("https://frontend.example.com/reset-password#token=");
        return link.substring(link.indexOf("#token=") + 7);
    }

    private ResultActions request(String email) throws Exception {
        return mvc.perform(post(PATH).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions apply(String token, String password) throws Exception {
        return mvc.perform(patch(PATH).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("token", token, "newPassword", password))));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").header("Origin", "https://frontend.example.com")
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("email", EMAIL, "password", password))));
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary MutableClock passwordResetClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        volatile Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
