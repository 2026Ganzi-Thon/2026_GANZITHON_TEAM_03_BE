UPDATE concerns
SET matures_at = created_at + INTERVAL 10 MINUTE
WHERE opened_at IS NULL AND matures_at > created_at + INTERVAL 10 MINUTE;
