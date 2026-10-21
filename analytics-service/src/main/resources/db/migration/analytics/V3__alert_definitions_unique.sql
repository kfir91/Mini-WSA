ALTER TABLE alert_definitions ADD COLUMN rule_key VARCHAR(128);

UPDATE alert_definitions
SET rule_key =
    metric || '|' || eval_window || '|' || CAST(threshold AS VARCHAR(16)) || '|' ||
    COALESCE(CAST(config_id AS VARCHAR(32)), '_') || '|' ||
    COALESCE(category, '_');

-- Keep the oldest row per rule signature; drop later duplicates.
DELETE FROM alert_definitions
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY rule_key
                   ORDER BY created_at ASC, id ASC
               ) AS rn
        FROM alert_definitions
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX uk_alert_definitions_rule_key ON alert_definitions (rule_key);
