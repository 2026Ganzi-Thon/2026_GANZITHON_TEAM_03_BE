package com.ganzithon.maeum.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganzithon.maeum.auth.repository.LocalCredentialRepository;
import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.session.AuthCookies;
import com.ganzithon.maeum.auth.verification.EmailVerificationService;
import com.ganzithon.maeum.auth.verification.VerificationMailSender;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.auth.jwt-secret-base64=dGVzdC1maXh0dXJlLW9ubHktMzItYnl0ZS1zaWduaW5nLWtleQ==",
        "app.auth.google-client-id=test-client",
        "app.auth.allowed-origins=https://frontend.example.com",
        "app.auth.terms-version=v1",
        "app.auth.privacy-version=v1",
        "app.auth.marketing-version=v1"
})
@ActiveProfiles({"mysql-integration", "auth"})
@AutoConfigureMockMvc
class LocalAuthMySqlIntegrationTest {
    private static final String ORIGIN = "https://frontend.example.com";
    private static final String EMAIL = "member@example.com";
    private static final String PASSWORD = " Abcdef1! ";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailVerificationService verifications;
    @Autowired LocalMemberStore members;
    @Autowired LocalCredentialRepository credentials;
    @MockitoBean VerificationMailSender mail;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void reset() {
        for (String table : List.of("refresh_tokens", "auth_sessions", "user_consents", "social_accounts",
                "local_credentials", "users", "auth_verifications", "email_verification_limits")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void verifiedSignupLogsInAndReusesTheExistingRefreshAndLogoutFlow() throws Exception {
        var confirmation = verifiedEmail();
        var signup = signup("  Member@EXAMPLE.COM  ", PASSWORD, confirmation.signupVerificationToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.user.profileCompleted").value(false))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        Cookie refresh = signup.getCookie(AuthCookies.REFRESH);
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(refresh.getMaxAge()).isEqualTo(1209600);

        var credential = credentials.findByEmail(EMAIL).orElseThrow();
        assertThat(credential.getEmailVerifiedAt()).isNotNull();
        assertThat(credential.getPasswordHash()).startsWith("{pbkdf2-sha256-600k}").doesNotContain(PASSWORD);
        assertThat(count("user_consents")).isEqualTo(3);
        assertThat(count("social_accounts")).isZero();
        String id = Long.toString(credential.getUserId());

        var rotated = mvc.perform(post("/api/v1/auth/refresh").header("Origin", ORIGIN).cookie(refresh))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(AuthCookies.REFRESH);
        mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(rotated))
                .andExpect(status().isNoContent());
        login(" MEMBER@EXAMPLE.COM ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.userId").value(id))
                .andExpect(jsonPath("$.data.isNewUser").doesNotExist());
        login(EMAIL, PASSWORD.strip()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));
    }

    @Test
    void passwordPolicyRejectionLeavesTheProofUsable() throws Exception {
        String proof = verifiedEmail().signupVerificationToken();
        for (String invalid : List.of("Abcd1!", "abcdef1!", "Abcdefg!", "Abcdef12", "Ab1!" + "x".repeat(61))) {
            signup(EMAIL, invalid, proof).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        }
        assertThat(count("users")).isZero();
        signup(EMAIL, "Abcdef1!", proof).andExpect(status().isCreated());
        signup(EMAIL, PASSWORD, proof).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SIGNUP_VERIFICATION_INVALID"));
        assertThat(count("users")).isEqualTo(1);
    }

    @Test
    void wrongEmailAndInvalidConsentCannotSpendTheProof() throws Exception {
        String proof = verifiedEmail().signupVerificationToken();
        signup("other@example.com", PASSWORD, proof).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SIGNUP_VERIFICATION_INVALID"));
        var request = signupBody(EMAIL, PASSWORD, proof);
        request.put("privacyAgreed", false);
        send("/api/v1/auth/signup", request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED_CONSENT_MISSING"));
        assertThat(count("users")).isZero();
        signup(EMAIL, PASSWORD, proof).andExpect(status().isCreated());
    }

    @Test
    void duplicateEmailHasConflictAndLoginFailureDoesNotRevealAccountExistence() throws Exception {
        members.create(EMAIL, PASSWORD);
        String proof = verifiedEmail().signupVerificationToken();
        signup(" MEMBER@EXAMPLE.COM ", PASSWORD, proof).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_DUPLICATED"));
        JsonNode unknown = mapper.readTree(login("unknown@example.com", PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        JsonNode unverified = mapper.readTree(login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        assertThat(unverified).isEqualTo(unknown);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("auth_sessions")).isZero();
    }

    @Test
    void parallelSignupRequestsSpendOneProofOnlyOnce() throws Exception {
        String proof = verifiedEmail().signupVerificationToken();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> signup = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
            return signup(EMAIL, PASSWORD, proof).andReturn().getResponse().getStatus();
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(signup);
            var second = pool.submit(signup);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 401);
        }
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("auth_sessions")).isEqualTo(1);
    }

    @Test
    void loginAndSignupRequireAnAllowedOrigin() throws Exception {
        for (String path : List.of("/api/v1/auth/login", "/api/v1/auth/signup")) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_FAILED"));
        }
    }

    private EmailVerificationService.Confirmation verifiedEmail() {
        verifications.send(EMAIL, "127.0.0.1");
        var token = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(EMAIL), token.capture());
        return verifications.confirm(token.getValue());
    }

    private ResultActions signup(String email, String password, String proof) throws Exception {
        return send("/api/v1/auth/signup", signupBody(email, password, proof));
    }

    private Map<String, Object> signupBody(String email, String password, String proof) {
        return new LinkedHashMap<>(Map.of("email", email, "password", password, "signupVerificationToken", proof,
                "termsVersion", "v1", "termsAgreed", true, "privacyVersion", "v1", "privacyAgreed", true));
    }

    private ResultActions login(String email, String password) throws Exception {
        return send("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions send(String path, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path).header("Origin", ORIGIN).contentType("application/json")
                .content(mapper.writeValueAsString(body)));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
