package com.ganzithon.maeum.auth;

import com.ganzithon.maeum.auth.repository.LocalCredentialRepository;
import com.ganzithon.maeum.auth.service.DuplicateLocalEmailException;
import com.ganzithon.maeum.auth.service.LocalMemberStore;
import com.ganzithon.maeum.auth.service.PasswordService;
import com.ganzithon.maeum.user.repository.UserRepository;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql-integration")
class MemberMySqlIntegrationTest {
    @Autowired LocalMemberStore members;
    @Autowired UserRepository users;
    @Autowired LocalCredentialRepository credentials;
    @Autowired PasswordService passwords;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void requireDedicatedDatabaseBeforeContextStarts(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void resetDedicatedTestDatabase() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM user_consents");
        jdbc.update("DELETE FROM social_accounts");
        jdbc.update("DELETE FROM local_credentials");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void migratesAndStoresAnUnverifiedMemberWithHashOnly() {
        Long id = members.create("  Member@EXAMPLE.COM  ", " Abcdef1! ");
        var user = users.findById(id).orElseThrow();
        var credential = credentials.findByEmail("member@example.com").orElseThrow();
        assertThat(credential.getUserId()).isEqualTo(id);
        assertThat(user.getContactEmail()).isEqualTo("member@example.com");
        assertThat(user.getNickname()).isNull();
        assertThat(user.isProfileCompleted()).isFalse();
        assertThat(user.isOnboardingCompleted()).isFalse();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(credential.getEmailVerifiedAt()).isNull();
        assertThat(passwords.matches(" Abcdef1! ", credential.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(3);
    }

    @Test
    void duplicateAndInvalidInputDoNotCreateOrphanUsers() {
        members.create("member@example.com", "Abcdef1!");
        assertThatThrownBy(() -> members.create(" MEMBER@EXAMPLE.COM ", "Abcdef1!"))
                .isInstanceOf(DuplicateLocalEmailException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> members.create("not-an-email", "Abcdef1!"));
        assertThatIllegalArgumentException().isThrownBy(() -> members.create("other@example.com", "short"));
        assertThat(users.count()).isEqualTo(1);
        assertThat(credentials.count()).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicateAndNonNormalizedEmailsEvenWithoutServiceCheck() {
        Long id = members.create("member@example.com", "Abcdef1!");
        assertThatThrownBy(() -> jdbc.update("UPDATE local_credentials SET email = ? WHERE user_id = ?", "MEMBER@example.com", id))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .satisfies(error -> assertThat(((java.sql.SQLException) error.getCause()).getErrorCode()).isEqualTo(3819));
        Long otherId = members.create("other@example.com", "Abcdef1!");
        assertThatThrownBy(() -> jdbc.update("UPDATE local_credentials SET email = ? WHERE user_id = ?", "member@example.com", otherId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentDuplicateRequestsKeepExactlyOneMember() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                try {
                    members.create("race@example.com", "Abcdef1!");
                    return true;
                } catch (DuplicateLocalEmailException | DataIntegrityViolationException expected) {
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(credentials.count()).isEqualTo(1);
    }
}
