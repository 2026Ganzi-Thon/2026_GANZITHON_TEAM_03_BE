package com.ganzithon.maeum.emotion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@SpringBootTest
@ActiveProfiles("mysql-integration")
@Transactional
class EmotionCatalogMySqlIntegrationTest {
    @Autowired JdbcTemplate db;

    @DynamicPropertySource
    static void requireDedicatedDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_MYSQL_URL");
        assertThat(url)
                .matches(
                        "jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/maeum_member_test[a-zA-Z0-9_]*(\\?.*)?");
        properties.add("spring.datasource.url", () -> url);
    }

    private void seed() {
        db.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            ScriptUtils.executeSqlScript(
                                    connection,
                                    new EncodedResource(
                                            new ClassPathResource(
                                                    "db/migration/V12__seed_emotions.sql"),
                                            StandardCharsets.UTF_8));
                            return null;
                        });
    }

    @Test
    void emptyCatalogGetsSevenActiveEmotionsInDesignOrder() {
        db.update("DELETE FROM emotions");
        seed();
        assertThat(
                        db.query(
                                "SELECT code,name FROM emotions ORDER BY display_order,id",
                                (rs, row) -> rs.getString("code") + ":" + rs.getString("name")))
                .containsExactly(
                        "JOY:기쁨",
                        "SADNESS:슬픔",
                        "REGRET:아쉬움",
                        "FEAR:두려움",
                        "TIRED:지침",
                        "ANXIETY:불안",
                        "CALM:평온");
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM emotions WHERE active=true", Integer.class))
                .isEqualTo(7);
        assertThat(
                        db.queryForList(
                                "SELECT display_order FROM emotions ORDER BY display_order,id",
                                Integer.class))
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void preservesManualEmotionIdCodeOrderAndActivation() {
        long id = db.queryForObject("SELECT id FROM emotions WHERE code='JOY'", Long.class);
        db.update(
                "UPDATE emotions SET code='MANUAL_JOY',display_order=99,active=false WHERE id=?",
                id);
        seed();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM emotions", Integer.class)).isEqualTo(7);
        assertThat(db.queryForObject("SELECT id FROM emotions WHERE name='기쁨'", Long.class))
                .isEqualTo(id);
        assertThat(db.queryForObject("SELECT code FROM emotions WHERE id=?", String.class, id))
                .isEqualTo("MANUAL_JOY");
        assertThat(db.queryForObject("SELECT active FROM emotions WHERE id=?", Boolean.class, id))
                .isFalse();
        assertThat(
                        db.queryForObject(
                                "SELECT display_order FROM emotions WHERE id=?", Integer.class, id))
                .isEqualTo(99);
    }

    @Test
    void flywayAppliesSeedAndRetriesDoNotDuplicateOrOverwrite() {
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND"
                                    + " version='12'",
                                Integer.class))
                .isEqualTo(1);
        var before = db.queryForList("SELECT * FROM emotions ORDER BY id");
        seed();
        seed();
        assertThat(db.queryForList("SELECT * FROM emotions ORDER BY id")).isEqualTo(before);
    }
}
