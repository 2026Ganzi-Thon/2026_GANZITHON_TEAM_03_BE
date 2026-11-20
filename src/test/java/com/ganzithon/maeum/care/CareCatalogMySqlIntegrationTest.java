package com.ganzithon.maeum.care;

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
class CareCatalogMySqlIntegrationTest {
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
                                                    "db/migration/V14__seed_care_actions.sql"),
                                            StandardCharsets.UTF_8));
                            return null;
                        });
    }

    @Test
    void emptyCatalogGetsSixteenActiveActionsWithFourWoolRewards() {
        db.update("DELETE FROM care_actions");
        seed();
        assertThat(
                        db.query(
                                "SELECT category,name FROM care_actions ORDER BY display_order,id",
                                (rs, row) -> rs.getString("category") + ":" + rs.getString("name")))
                .containsExactly(
                        "움직이기:물 한 잔 마시기",
                        "움직이기:간단한 간식 먹기",
                        "움직이기:10분 산책하기",
                        "움직이기:편안한 옷으로 갈아입기",
                        "마음돌봄:양 먹이주기: 오늘의 마음을 한 줄로 적기",
                        "마음돌봄:오늘 내가 애쓴 일 하나 알아주기",
                        "마음돌봄:내 기분을 색이나 낙서로 표현하기",
                        "마음돌봄:편한 사람에게 내 기분 이야기하기",
                        "쉬어가기:동굴 5분 닫기: 휴대폰을 5분 뒤집어보세요",
                        "쉬어가기:불필요한 알림 잠시 끄기",
                        "쉬어가기:좋아하는 음악 한 곡 듣기",
                        "쉬어가기:따뜻한 음료 천천히 마시기",
                        "일상가꾸기:침구 정리하기",
                        "일상가꾸기:책상 위 물건 정리하기",
                        "일상가꾸기:사용한 컵 하나 씻기",
                        "일상가꾸기:마음에 드는 순간 사진으로 남기기");
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM care_actions WHERE reward_amount=4 AND"
                                    + " active=true",
                                Integer.class))
                .isEqualTo(16);
        assertThat(
                        db.queryForList(
                                "SELECT display_order FROM care_actions ORDER BY display_order,id",
                                Integer.class))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
    }

    @Test
    void preservesManualIdsRewardsOrderAndActivationWhileFillingCategory() {
        long id =
                db.queryForObject("SELECT id FROM care_actions WHERE name='물 한 잔 마시기'", Long.class);
        db.update(
                "UPDATE care_actions SET reward_amount=9,description='운영 설정',display_order=99,"
                        + "active=false,category='기타' WHERE id=?",
                id);
        db.update(
                "INSERT INTO care_actions(name,description,reward_amount,display_order,active)"
                        + " VALUES('직접 등록한 행동',NULL,2,100,true)");
        seed();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM care_actions", Integer.class))
                .isEqualTo(17);
        assertThat(
                        db.queryForObject(
                                "SELECT id FROM care_actions WHERE name='물 한 잔 마시기'", Long.class))
                .isEqualTo(id);
        var action = db.queryForMap("SELECT * FROM care_actions WHERE id=?", id);
        assertThat(action.get("name")).isEqualTo("물 한 잔 마시기");
        assertThat(action.get("description")).isEqualTo("운영 설정");
        assertThat(((Number) action.get("reward_amount")).intValue()).isEqualTo(9);
        assertThat(((Number) action.get("display_order")).intValue()).isEqualTo(99);
        assertThat(
                        db.queryForObject(
                                "SELECT active FROM care_actions WHERE id=?", Boolean.class, id))
                .isFalse();
        assertThat(action.get("category")).isEqualTo("움직이기");
        assertThat(
                        db.queryForObject(
                                "SELECT category FROM care_actions WHERE name='직접 등록한 행동'",
                                String.class))
                .isEqualTo("기타");
    }

    @Test
    void flywayAppliesNewVersionsAndSeedRetriesDoNotDuplicateOrOverwrite() {
        assertThat(
                        db.queryForList(
                                "SELECT version FROM flyway_schema_history WHERE success=1 AND"
                                    + " version IN ('13','14') ORDER BY installed_rank",
                                String.class))
                .containsExactly("13", "14");
        var before = db.queryForList("SELECT * FROM care_actions ORDER BY id");
        seed();
        seed();
        assertThat(db.queryForList("SELECT * FROM care_actions ORDER BY id")).isEqualTo(before);
    }
}
