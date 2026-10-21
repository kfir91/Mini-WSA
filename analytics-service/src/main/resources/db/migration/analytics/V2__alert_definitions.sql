CREATE TABLE alert_definitions (
    id VARCHAR(64) PRIMARY KEY,
    config_id BIGINT NULL,
    category VARCHAR(64) NULL,
    metric VARCHAR(32) NOT NULL,
    eval_window VARCHAR(8) NOT NULL,
    threshold INTEGER NOT NULL CHECK (threshold >= 1),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_alert_definitions_config ON alert_definitions (config_id);
