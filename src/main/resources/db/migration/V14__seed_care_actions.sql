-- Preserve manually configured action IDs, order, activation and reward amounts.
INSERT INTO care_actions (name, description, reward_amount, display_order, active, category)
SELECT seed.name, NULL, 4, seed.display_order, true, seed.category
FROM (
    SELECT '물 한 잔 마시기' AS name, '움직이기' AS category, 1 AS display_order
    UNION ALL
    SELECT '간단한 간식 먹기' AS name, '움직이기' AS category, 2 AS display_order
    UNION ALL
    SELECT '10분 산책하기' AS name, '움직이기' AS category, 3 AS display_order
    UNION ALL
    SELECT '편안한 옷으로 갈아입기' AS name, '움직이기' AS category, 4 AS display_order
    UNION ALL
    SELECT '양 먹이주기: 오늘의 마음을 한 줄로 적기' AS name, '마음돌봄' AS category, 5 AS display_order
    UNION ALL
    SELECT '오늘 내가 애쓴 일 하나 알아주기' AS name, '마음돌봄' AS category, 6 AS display_order
    UNION ALL
    SELECT '내 기분을 색이나 낙서로 표현하기' AS name, '마음돌봄' AS category, 7 AS display_order
    UNION ALL
    SELECT '편한 사람에게 내 기분 이야기하기' AS name, '마음돌봄' AS category, 8 AS display_order
    UNION ALL
    SELECT '동굴 5분 닫기: 휴대폰을 5분 뒤집어보세요' AS name, '쉬어가기' AS category, 9 AS display_order
    UNION ALL
    SELECT '불필요한 알림 잠시 끄기' AS name, '쉬어가기' AS category, 10 AS display_order
    UNION ALL
    SELECT '좋아하는 음악 한 곡 듣기' AS name, '쉬어가기' AS category, 11 AS display_order
    UNION ALL
    SELECT '따뜻한 음료 천천히 마시기' AS name, '쉬어가기' AS category, 12 AS display_order
    UNION ALL
    SELECT '침구 정리하기' AS name, '일상가꾸기' AS category, 13 AS display_order
    UNION ALL
    SELECT '책상 위 물건 정리하기' AS name, '일상가꾸기' AS category, 14 AS display_order
    UNION ALL
    SELECT '사용한 컵 하나 씻기' AS name, '일상가꾸기' AS category, 15 AS display_order
    UNION ALL
    SELECT '마음에 드는 순간 사진으로 남기기' AS name, '일상가꾸기' AS category, 16 AS display_order
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM care_actions existing WHERE existing.name = seed.name
);

-- Classify pre-existing standard actions without changing their business settings.
UPDATE care_actions
SET category = CASE
    WHEN name IN ('물 한 잔 마시기', '간단한 간식 먹기', '10분 산책하기', '편안한 옷으로 갈아입기') THEN '움직이기'
    WHEN name IN ('양 먹이주기: 오늘의 마음을 한 줄로 적기', '오늘 내가 애쓴 일 하나 알아주기', '내 기분을 색이나 낙서로 표현하기', '편한 사람에게 내 기분 이야기하기') THEN '마음돌봄'
    WHEN name IN ('동굴 5분 닫기: 휴대폰을 5분 뒤집어보세요', '불필요한 알림 잠시 끄기', '좋아하는 음악 한 곡 듣기', '따뜻한 음료 천천히 마시기') THEN '쉬어가기'
    WHEN name IN ('침구 정리하기', '책상 위 물건 정리하기', '사용한 컵 하나 씻기', '마음에 드는 순간 사진으로 남기기') THEN '일상가꾸기'
    ELSE category
END
WHERE category = '기타';
