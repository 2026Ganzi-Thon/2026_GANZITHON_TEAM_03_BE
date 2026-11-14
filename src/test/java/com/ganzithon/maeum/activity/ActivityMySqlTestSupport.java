package com.ganzithon.maeum.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganzithon.maeum.auth.service.LocalMemberStore;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "app.auth.jwt-secret-base64=dGVzdC1maXh0dXJlLW9ubHktMzItYnl0ZS1zaWduaW5nLWtleQ==",
            "app.auth.google-client-id=test-client",
            "app.auth.allowed-origins=https://frontend.example.com",
            "app.auth.terms-version=v1",
            "app.auth.privacy-version=v1",
            "app.auth.marketing-version=v1"
        })
@ActiveProfiles({"mysql-integration", "auth"})
@AutoConfigureMockMvc
@Import(ActivityMySqlTestSupport.ClockConfig.class)
public abstract class ActivityMySqlTestSupport {
    private static final String ORIGIN = "https://frontend.example.com";
    private static final String PASSWORD = "Abcdef1!";
    private static final Instant START = Instant.parse("2026-11-10T03:00:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock activityClock() {
            return new MutableClock();
        }
    }

    protected static class MutableClock extends Clock {
        public volatile Instant now = START;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired LocalMemberStore members;
    @Autowired protected JdbcTemplate db;
    @Autowired protected MutableClock clock;

    protected long user;
    protected long other;
    private final Map<Long, String> tokens = new HashMap<>();

    @BeforeEach
    void loginMembers() throws Exception {
        clock.now = START;
        user = login();
        other = login();
    }

    private long login() throws Exception {
        String email = "activity-" + UUID.randomUUID() + "@example.com";
        long id = members.createVerified(email, PASSWORD, clock.instant()).getId();
        String body =
                mapper.createObjectNode().put("email", email).put("password", PASSWORD).toString();
        var result = call(200, json(post("/api/v1/auth/login"), body).header("Origin", ORIGIN));
        tokens.put(id, result.at("/data/accessToken").asText());
        return id;
    }

    protected String token(long member) {
        return tokens.get(member);
    }

    protected String key() {
        return UUID.randomUUID().toString();
    }

    protected JsonNode call(int status, MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        String body = response.getContentAsString();
        return body.isBlank() ? mapper.nullNode() : mapper.readTree(body);
    }

    protected MockHttpServletRequestBuilder auth(
            MockHttpServletRequestBuilder request, long member) {
        return request.header("Authorization", "Bearer " + token(member));
    }

    protected MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder request, String body) {
        return request.contentType("application/json").content(body);
    }

    protected void clearFixtures(String... tables) {
        for (String table : tables) {
            db.update("DELETE FROM " + table + " WHERE user_id IN (?,?)", user, other);
        }
        db.update(
                "DELETE FROM refresh_tokens WHERE session_id IN (SELECT id FROM auth_sessions WHERE"
                    + " user_id IN (?,?))",
                user,
                other);
        db.update("DELETE FROM auth_sessions WHERE user_id IN (?,?)", user, other);
        db.update("DELETE FROM local_credentials WHERE user_id IN (?,?)", user, other);
        db.update("DELETE FROM users WHERE id IN (?,?)", user, other);
    }
}
