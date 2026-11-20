package com.ganzithon.maeum.user;

import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.session.AuthSessionService;
import com.ganzithon.maeum.user.consent.ConsentHistory;
import java.time.Instant;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.auth.jwt-secret-base64=dGVzdC1maXh0dXJlLW9ubHktMzItYnl0ZS1zaWduaW5nLWtleQ==",
        "app.auth.google-client-id=test-client",
        "app.auth.allowed-origins=https://frontend.example.com",
        "app.auth.terms-version=v1",
        "app.auth.privacy-version=v1",
        "app.auth.marketing-version=v2"
})
@ActiveProfiles({"mysql-integration", "auth"})
@AutoConfigureMockMvc
class MemberConsentMySqlIntegrationTest {
    private static final String PATH = "/api/v1/users/me/consents";
    private static final Instant RECORDED = Instant.parse("2026-11-07T06:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsentHistory history;
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
    void readsOnlyOwnersLatestEntryPerTypeAndBreaksTimestampTiesById() throws Exception {
        var member = account("member@example.com");
        var other = account("other@example.com");
        history.append(member.id(), "TERMS", "v1", true, RECORDED);
        history.append(member.id(), "PRIVACY", "v1", true, RECORDED);
        history.append(member.id(), "MARKETING", "v1", true, RECORDED);
        history.append(member.id(), "MARKETING", "v1", false, RECORDED);
        history.append(other.id(), "MARKETING", "other", true, RECORDED.plusSeconds(1));

        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).param("userId", Long.toString(other.id())).header("Authorization", member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].consentType").value("TERMS"))
                .andExpect(jsonPath("$.data.items[1].consentType").value("PRIVACY"))
                .andExpect(jsonPath("$.data.items[2].consentType").value("MARKETING"))
                .andExpect(jsonPath("$.data.items[2].termsVersion").value("v1"))
                .andExpect(jsonPath("$.data.items[2].agreed").value(false))
                .andExpect(jsonPath("$.data.items[2].recordedAt").value(RECORDED.toString()));
    }

    @Test
    void oldVersionCannotGrantConsentButCanBeWithdrawnAndRetried() throws Exception {
        var member = account("member@example.com");
        history.append(member.id(), "MARKETING", "v1", true, RECORDED);

        update(member, true, "v1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_MISMATCH"));
        String withdrawal = update(member, false, "v2").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreed").value(false))
                .andExpect(jsonPath("$.data.termsVersion").value("v1"))
                .andReturn().getResponse().getContentAsString();
        update(member, false, "v1").andExpect(status().isOk()).andExpect(content().json(withdrawal));
        update(member, true, "v2").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreed").value(true))
                .andExpect(jsonPath("$.data.termsVersion").value("v2"));
        assertThat(historyCount(member.id())).isEqualTo(3);
    }

    @Test
    void concurrentIdenticalUpdatesCreateOneEntryAndReturnTheSameState() throws Exception {
        var member = account("member@example.com");
        mvc.perform(get(PATH).header("Authorization", member.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var request = (java.util.concurrent.Callable<String>) () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                return update(member, true, "v2").andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(historyCount(member.id())).isEqualTo(1);
    }

    private ResultActions update(Account member, boolean agreed, String version) throws Exception {
        return mvc.perform(patch(PATH + "/marketing").header("Authorization", member.bearer())
                .contentType("application/json")
                .content("{\"agreed\":" + agreed + ",\"termsVersion\":\"" + version + "\"}"));
    }

    private int historyCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_consents WHERE user_id = ?", Integer.class, userId);
    }

    private Account account(String email) {
        long id = members.create(email, "Abcdef1!");
        String token = transactions.execute(status -> sessions.create(id).accessToken());
        return new Account(id, "Bearer " + token);
    }

    private record Account(long id, String bearer) { }
}
