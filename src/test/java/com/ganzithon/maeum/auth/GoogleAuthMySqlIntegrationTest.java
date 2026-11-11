package com.ganzithon.maeum.auth;

import com.fasterxml.jackson.databind.*;
import com.ganzithon.maeum.auth.google.GoogleIdentityVerifier;
import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.session.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.auth.jwt-secret-base64=dGVzdC1maXh0dXJlLW9ubHktMzItYnl0ZS1zaWduaW5nLWtleQ==",
    "app.auth.google-client-id=test-client", "app.auth.allowed-origins=https://frontend.example.com",
    "app.auth.cookie-same-site=Lax", "app.auth.terms-version=v1", "app.auth.privacy-version=v1", "app.auth.marketing-version=v1"})
@ActiveProfiles({"mysql-integration", "auth"})
@AutoConfigureMockMvc
@Import({GoogleAuthMySqlIntegrationTest.Config.class, GoogleAuthMySqlIntegrationTest.Probe.class})
class GoogleAuthMySqlIntegrationTest {
    static final String ORIGIN = "https://frontend.example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalMemberStore members;
    @Autowired MutableClock clock;
    @Autowired @Qualifier("serviceJwtDecoder") JwtDecoder decoder;
    @MockitoBean GoogleIdentityVerifier verifier;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }
    @BeforeEach void reset() {
        for (String table : List.of("refresh_tokens", "auth_sessions", "user_consents", "social_accounts", "google_login_challenges", "auth_identity_locks", "local_credentials", "users")) jdbc.update("DELETE FROM " + table);
        clock.now = Instant.parse("2026-11-10T12:00:00Z");
        when(verifier.verify(anyString(), anyString())).thenReturn(new GoogleIdentityVerifier.Identity("google-sub", "member@example.com", null));
    }
    String nonce() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/google/challenges").header("Origin", ORIGIN))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "Secure", "SameSite=Lax");
        return mapper.readTree(response.getContentAsString()).at("/data/nonce").asText();
    }
    MvcResult login(String nonce, boolean consent) throws Exception {
        String body = consent ? "{\"credential\":\"google-id-token\",\"termsAgreed\":true,\"termsVersion\":\"v1\",\"privacyAgreed\":true,\"privacyVersion\":\"v1\"}" : "{\"credential\":\"google-id-token\"}";
        return mvc.perform(post("/api/v1/auth/google").header("Origin", ORIGIN).cookie(new Cookie(AuthCookies.CHALLENGE, nonce))
                .contentType("application/json").content(body)).andReturn();
    }
    String refresh(MvcResult result) { return result.getResponse().getCookie(AuthCookies.REFRESH).getValue(); }
    String access(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText(); }
    MvcResult rotate(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN)
                .header("Authorization", "Bearer expired-token-is-ignored")
                .cookie(new Cookie(AuthCookies.REFRESH, token))).andReturn();
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void newAndReturningLoginKeepPrivateTokensInCookies() throws Exception {
        var first = login(nonce(), true);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = mapper.readTree(first.getResponse().getContentAsString()).get("data");
        assertThat(data.get("isNewUser").asBoolean()).isTrue();
        assertThat(data.get("user").get("userId").isTextual()).isTrue();
        assertThat(data.has("refreshToken")).isFalse();
        assertThat(data.get("user").get("profileCompleted").asBoolean()).isFalse();
        assertThat(decoder.decode(access(first)).getExpiresAt()).isEqualTo(clock.instant().plusSeconds(3600));
        assertThat(count("user_consents")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class)).isNotEqualTo(refresh(first));
        var returning = login(nonce(), false);
        assertThat(returning.getResponse().getStatus()).isEqualTo(200);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("auth_sessions")).isEqualTo(2);
    }
    @Test void missingConsentRollsBackAndAllowsRetryAndDoesNotLinkLocalEmail() throws Exception {
        members.create("member@example.com", "Abcdef1!");
        String challenge = nonce();
        var rejected = login(challenge, false);
        assertThat(rejected.getResponse().getStatus()).isEqualTo(409);
        assertThat(count("social_accounts")).isZero();
        assertThat(login(challenge, true).getResponse().getStatus()).isEqualTo(201);
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("local_credentials")).isEqualTo(1);
    }
    @Test void consumedChallengeCannotCreateAnotherSession() throws Exception {
        String challenge = nonce();
        assertThat(login(challenge, true).getResponse().getStatus()).isEqualTo(201);
        assertThat(login(challenge, true).getResponse().getStatus()).isEqualTo(401);
        assertThat(count("auth_sessions")).isEqualTo(1);
    }
    @Test void refreshRotatesAndReuseRevokesAllTokensInThatSession() throws Exception {
        var first = login(nonce(), true);
        clock.now = clock.now.plusSeconds(7200);
        var rotated = rotate(refresh(first));
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        assertThat(refresh(rotated)).isNotEqualTo(refresh(first));
        assertThat(jdbc.queryForObject("SELECT expires_at FROM refresh_tokens WHERE used_at IS NULL", java.time.LocalDateTime.class))
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plusSeconds(1209600), ZoneOffset.UTC));
        decoder.decode(access(rotated));
        var reuse = rotate(refresh(first));
        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(reuse.getResponse().getContentAsString()).contains("AUTH_REFRESH_TOKEN_REUSED");
        assertThatThrownBy(() -> decoder.decode(access(rotated))).isInstanceOf(JwtException.class);
        assertThat(rotate(refresh(rotated)).getResponse().getStatus()).isEqualTo(401);
    }
    @Test void concurrentRefreshDetectsReuseRatherThanIssuingTwoSuccesses() throws Exception {
        String raw = refresh(login(nonce(), true));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            Callable<Integer> attempt = () -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); return rotate(raw).getResponse().getStatus(); };
            var a = pool.submit(attempt); var b = pool.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }
    @Test void concurrentFirstLoginCreatesExactlyOneGoogleMember() throws Exception {
        String firstNonce = nonce(); String secondNonce = nonce();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
            java.util.function.Function<String, Callable<Integer>> attempt = nonce -> () -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException();
                return login(nonce, true).getResponse().getStatus();
            };
            var a = pool.submit(attempt.apply(firstNonce)); var b = pool.submit(attempt.apply(secondNonce));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 200);
        }
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("social_accounts")).isEqualTo(1);
        assertThat(count("auth_sessions")).isEqualTo(2);
    }
    @Test void logoutRevokesAccessAndIsIdempotent() throws Exception {
        var first = login(nonce(), true);
        mvc.perform(post("/api/v1/auth/test-probe").header("Authorization", "Bearer " + access(first))).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN)
                .cookie(new Cookie(AuthCookies.REFRESH, refresh(first)))).andExpect(status().isNoContent());
        assertThatThrownBy(() -> decoder.decode(access(first))).isInstanceOf(JwtException.class);
        mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN)).andExpect(status().isNoContent());
    }
    @Test void expiredAccessAndRefreshAreRejected() throws Exception {
        var first = login(nonce(), true);
        clock.now = clock.now.plusSeconds(3600);
        mvc.perform(get("/api/v1/auth/test-probe").header("Authorization", "Bearer " + access(first)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_ACCESS_TOKEN_EXPIRED"));
        clock.now = clock.now.plusSeconds(1209600);
        assertThat(rotate(refresh(first)).getResponse().getContentAsString()).contains("AUTH_REFRESH_TOKEN_EXPIRED");
    }
    @Test void originAndBearerProtectionRejectAnonymousOrCrossSiteMutations() throws Exception {
        mvc.perform(post("/api/v1/auth/google/challenges")).andExpect(status().isForbidden());
        mvc.perform(post(java.net.URI.create("/api/v1/auth/%6cogout"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header("Origin", "https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/test-probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/test-probe").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        assertThat(count("auth_sessions")).isZero();
    }
    @Test void expiredChallengeAndOutdatedConsentAreRejectedWithoutMemberCreation() throws Exception {
        String challenge = nonce(); clock.now = clock.now.plusSeconds(300);
        assertThat(login(challenge, true).getResponse().getStatus()).isEqualTo(401);
        mvc.perform(post("/api/v1/auth/google").header("Origin", ORIGIN).cookie(new Cookie(AuthCookies.CHALLENGE, nonce()))
                .contentType("application/json").content("{\"credential\":\"token\",\"termsAgreed\":true,\"termsVersion\":\"old\",\"privacyAgreed\":true,\"privacyVersion\":\"v1\"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("users")).isZero();
    }
    @TestConfiguration static class Config {
        @Bean @Primary MutableClock mutableAuthClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-10T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @RestController static class Probe {
        @RequestMapping(value = "/api/v1/auth/test-probe", method = {RequestMethod.GET, RequestMethod.POST})
        String probe() { return "ok"; }
    }
}
