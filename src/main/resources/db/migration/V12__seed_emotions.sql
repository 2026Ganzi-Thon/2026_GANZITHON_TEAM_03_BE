-- Preserve manually configured emotion IDs, codes, order and activation.
INSERT INTO emotions (code, name, display_order, active)
SELECT seed.code, seed.name, seed.display_order, true
FROM (
    SELECT 'JOY' AS code, '기쁨' AS name, 1 AS display_order
    UNION ALL
    SELECT 'SADNESS' AS code, '슬픔' AS name, 2 AS display_order
    UNION ALL
    SELECT 'REGRET' AS code, '아쉬움' AS name, 3 AS display_order
    UNION ALL
    SELECT 'FEAR' AS code, '두려움' AS name, 4 AS display_order
    UNION ALL
    SELECT 'TIRED' AS code, '지침' AS name, 5 AS display_order
    UNION ALL
    SELECT 'ANXIETY' AS code, '불안' AS name, 6 AS display_order
    UNION ALL
    SELECT 'CALM' AS code, '평온' AS name, 7 AS display_order
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM emotions existing
    WHERE existing.code = seed.code OR existing.name = seed.name
);
