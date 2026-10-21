CREATE TABLE security_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    config_id BIGINT NOT NULL,
    policy_id VARCHAR(128) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    method VARCHAR(16) NOT NULL,
    status_code INTEGER NOT NULL,
    user_agent VARCHAR(1024) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_message VARCHAR(1024) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    country VARCHAR(8) NOT NULL,
    city VARCHAR(128) NOT NULL,
    request_size INTEGER NOT NULL,
    response_size INTEGER NOT NULL,
    attack_type VARCHAR(128) NOT NULL,
    threat_score INTEGER NOT NULL
);

CREATE INDEX idx_events_config_ts ON security_events (config_id, event_timestamp);
CREATE INDEX idx_events_ip_ts ON security_events (client_ip, event_timestamp);
CREATE INDEX idx_events_category_ts ON security_events (category, event_timestamp);
CREATE INDEX idx_events_action_ts ON security_events (action, event_timestamp);
