-- 고민 제목 칸을 추가합니다. 기존 행이 있어도 실패하지 않도록 임시 기본값으로 먼저 추가합니다.
ALTER TABLE concerns ADD COLUMN title VARCHAR(50) NOT NULL DEFAULT '' AFTER user_id;

-- 기존 고민은 본문 첫 줄(앞뒤 공백 제거, 최대 50자)로 채우고, 비면 '제목 없음'으로 둡니다.
UPDATE concerns
SET title = COALESCE(
    NULLIF(
        REGEXP_REPLACE(
            LEFT(REGEXP_REPLACE(SUBSTRING_INDEX(content, CHAR(10 USING utf8mb4), 1), '^[[:space:]]+', ''), 50),
            '[[:space:]]+$', ''),
        ''),
    '제목 없음');

-- 이후 작성은 앱이 제목을 반드시 넣으므로 임시 기본값을 없앱니다.
ALTER TABLE concerns ALTER COLUMN title DROP DEFAULT;
