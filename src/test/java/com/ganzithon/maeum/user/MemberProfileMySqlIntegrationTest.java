package com.ganzithon.maeum.user;

import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.session.AuthSessionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class MemberProfileMySqlIntegrationTest {
    private static final String ME = "/api/v1/users/me";
    private static final String INITIAL = ME + "/initial-profile";
    private static final String ONBOARDING = ME + "/onboarding";
    private static final String NICKNAME = "/api/v1/users/nickname-availability";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalMemberStore members;
    @Autowired AuthSessionService sessions;
    @Autowired TransactionTemplate transactions;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void clearMembers() {
        for (String table : List.of("refresh_tokens", "auth_sessions", "user_consents", "social_accounts",
                "local_credentials", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void readsOnlyTheTokenOwnerAndKeepsTheGooglePicture() throws Exception {
        var member = account("member@example.com");
        var other = account("other@example.com");
        jdbc.update("UPDATE users SET profile_image_url = ? WHERE id = ?",
                "https://example.com/picture", member.id());
        jdbc.update("INSERT INTO social_accounts (user_id, provider, provider_user_id, created_at) VALUES (?, 'GOOGLE', ?, UTC_TIMESTAMP(6))",
                member.id(), "test-google-sub");

        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).param("userId", Long.toString(other.id())).header("Authorization", member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(Long.toString(member.id())))
                .andExpect(jsonPath("$.data.profileCompleted").value(false))
                .andExpect(jsonPath("$.data.contactEmail").value("member@example.com"))
                .andExpect(jsonPath("$.data.loginProviders[0]").value("LOCAL"))
                .andExpect(jsonPath("$.data.loginProviders[1]").value("GOOGLE"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/picture"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void initialSetupCanBeRetriedAndOnlyExplicitFieldsAreChanged() throws Exception {
        var member = account("member@example.com");
        String initial = """
                {"nickname":"  온기_1  ","introduction":"안녕하세요","ageGroup":"20s","regionCode":"SEOUL"}
                """;
        for (int i = 0; i < 2; i++) {
            patch(INITIAL, member, initial).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("온기_1"))
                    .andExpect(jsonPath("$.data.profileCompleted").value(true));
        }
        patch(INITIAL, member, """
                {"nickname":"다른이름"}
                """).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_INITIAL_PROFILE_ALREADY_COMPLETED"));
        patch(ME, member, """
                {"introduction":null,"ageGroup":null}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introduction").value(nullValue()))
                .andExpect(jsonPath("$.data.ageGroup").value(nullValue()))
                .andExpect(jsonPath("$.data.nickname").value("온기_1"))
                .andExpect(jsonPath("$.data.regionCode").value("SEOUL"));
        patch(ME, member, """
                {"nickname":null}
                """).andExpect(status().isBadRequest());
        patch(ME, member, """
                {"userId":"1"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void availabilityAndSaveUseTheSameNicknameRules() throws Exception {
        var owner = account("owner@example.com");
        var other = account("other@example.com");
        available(owner, "a").andExpect(status().isBadRequest());
        available(owner, "123456789").andExpect(status().isBadRequest());
        patch(INITIAL, owner, """
                {"nickname":"123456789"}
                """).andExpect(status().isBadRequest());
        patch(INITIAL, owner, """
                {"nickname":"온기123456"}
                """).andExpect(status().isOk());
        available(owner, " 온기123456 ").andExpect(jsonPath("$.data.available").value(true));
        available(other, "온기123456").andExpect(jsonPath("$.data.available").value(false));
        patch(INITIAL, other, """
                {"nickname":"온기123456"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_NICKNAME_DUPLICATED"));
        mvc.perform(get(ME).header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data.profileCompleted").value(false));
    }

    @Test
    void concurrentNicknameClaimsReturnOneSuccessAndOneConflict() throws Exception {
        var first = account("first@example.com");
        var second = account("second@example.com");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(claim(first, ready, start));
            var b = pool.submit(claim(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE profile_completed_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void onboardingAcceptsOnlyTrueAndPreservesTheFirstTimestamp() throws Exception {
        var member = account("member@example.com");
        patch(ONBOARDING, member, """
                {"completed":false}
                """).andExpect(status().isBadRequest());
        patch(ONBOARDING, member, """
                {"completed":"true"}
                """).andExpect(status().isBadRequest());
        var first = patch(ONBOARDING, member, """
                {"completed":true}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.data.onboardingCompleted").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbc.queryForObject("SELECT onboarding_completed_at FROM users WHERE id = ?",
                LocalDateTime.class, member.id())).isNotNull();
        patch(ONBOARDING, member, """
                {"completed":true}
                """).andExpect(status().isOk()).andExpect(content().json(first));
    }

    private Callable<Integer> claim(Account member, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
            return patch(INITIAL, member, """
                    {"nickname":"같은이름"}
                    """).andReturn().getResponse().getStatus();
        };
    }

    private Account account(String email) {
        long id = members.create(email, "Abcdef1!");
        String token = transactions.execute(status -> sessions.create(id).accessToken());
        return new Account(id, "Bearer " + token);
    }

    private ResultActions available(Account account, String nickname) throws Exception {
        return mvc.perform(get(NICKNAME).param("nickname", nickname).header("Authorization", account.bearer()));
    }

    private ResultActions patch(String path, Account account, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path)
                .header("Authorization", account.bearer()).contentType("application/json").content(body));
    }

    private record Account(long id, String bearer) { }
}
