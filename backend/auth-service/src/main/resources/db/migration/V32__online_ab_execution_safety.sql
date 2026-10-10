-- 线上 schema 2 安全内核；已发布/提交迁移不回改。默认不开启真实实验。
ALTER TABLE online_experiment MODIFY consumed_tokens DECIMAL(38,0) NOT NULL DEFAULT 0,
    ADD control_authorized_by BIGINT DEFAULT NULL;
ALTER TABLE online_assignment MODIFY consumed_tokens DECIMAL(38,0) DEFAULT NULL,
    ADD execution_status VARCHAR(32) DEFAULT NULL,
    ADD execution_terminal_at DATETIME(3) DEFAULT NULL,
    ADD last_observed_at DATETIME(3) DEFAULT NULL,
    ADD unresolved_since DATETIME(3) DEFAULT NULL,
    ADD KEY idx_online_assignment_health (experiment_id,variant,execution_terminal_at,id);
ALTER TABLE online_execution_slot ADD started_at DATETIME(3) DEFAULT NULL,
    ADD created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
CREATE TABLE online_experiment_event (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, space_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL, actor_type VARCHAR(16) NOT NULL,
    actor_id VARCHAR(64) NOT NULL, authorized_by BIGINT DEFAULT NULL,
    state_version BIGINT NOT NULL, reason_code VARCHAR(64) DEFAULT NULL,
    assignment_id BIGINT DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), KEY idx_online_event_experiment(experiment_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='无正文和凭证的追加安全事件';
