-- 受控线上 A/B 基础；不回写离线快照或报告，不在本迁移启用流量。
CREATE TABLE online_experiment (
    id BIGINT NOT NULL, space_id BIGINT NOT NULL, agent_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    client_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_schema_version INT NOT NULL, manifest_json LONGTEXT NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED', active_slot TINYINT DEFAULT NULL,
    authorized_token_budget BIGINT NOT NULL, max_task_count INT NOT NULL,
    assigned_task_count INT NOT NULL DEFAULT 0, reserved_token_budget BIGINT NOT NULL DEFAULT 0,
    consumed_tokens BIGINT NOT NULL DEFAULT 0, reason_code VARCHAR(64) DEFAULT NULL,
    created_by BIGINT NOT NULL, state_changed_by BIGINT DEFAULT NULL, state_changed_at DATETIME(3) DEFAULT NULL,
    started_at DATETIME(3) DEFAULT NULL, stop_requested_at DATETIME(3) DEFAULT NULL, stopped_at DATETIME(3) DEFAULT NULL,
    decision VARCHAR(32) DEFAULT NULL, decision_reason VARCHAR(2000) DEFAULT NULL,
    decision_report_revision INT DEFAULT NULL, decided_by BIGINT DEFAULT NULL, decided_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_online_experiment_request (space_id, client_request_key),
    UNIQUE KEY uk_online_experiment_active (space_id, agent_id, active_slot),
    KEY idx_online_experiment_search (space_id, status, id),
    KEY idx_online_experiment_agent (space_id, agent_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受控线上实验冻结配置与门禁';

CREATE TABLE online_assignment (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, space_id BIGINT NOT NULL, agent_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL, document_id BIGINT NOT NULL, created_by BIGINT NOT NULL,
    client_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_snapshot_schema_version INT NOT NULL,
    input_snapshot_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bucket INT NOT NULL, variant VARCHAR(16) NOT NULL,
    candidate_config_id BIGINT DEFAULT NULL, config_schema_version INT NOT NULL,
    config_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_schema_version INT NOT NULL,
    binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reserved_token_budget BIGINT NOT NULL,
    task_confirmation_status VARCHAR(16) NOT NULL DEFAULT 'UNCONFIRMED',
    settlement_status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    execution_id BIGINT DEFAULT NULL, consumed_tokens BIGINT DEFAULT NULL,
    reason_code VARCHAR(64) DEFAULT NULL, task_confirmed_at DATETIME(3) DEFAULT NULL,
    settled_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_online_assignment_task (task_id),
    UNIQUE KEY uk_online_assignment_request (experiment_id, created_by, client_request_key),
    KEY idx_online_assignment_experiment (experiment_id, id),
    KEY idx_online_assignment_settlement (experiment_id, settlement_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='线上任务不可变分组与预算结算';

CREATE TABLE online_evaluation_attempt (
    id BIGINT NOT NULL, assignment_id BIGINT NOT NULL, space_id BIGINT NOT NULL,
    evaluator_version_id BIGINT NOT NULL, attempt_no INT NOT NULL,
    client_request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    claim_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    claim_expires_at DATETIME(3) DEFAULT NULL, result_id BIGINT DEFAULT NULL,
    reason_code VARCHAR(64) DEFAULT NULL,
    started_at DATETIME(3) DEFAULT NULL, finished_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_online_evaluation_attempt (assignment_id, evaluator_version_id, attempt_no),
    UNIQUE KEY uk_online_evaluation_request (assignment_id, evaluator_version_id, client_request_key),
    UNIQUE KEY uk_online_evaluation_result (result_id),
    KEY idx_online_evaluation_claim (status, claim_expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='线上评价尝试，不控制任务终态';

CREATE TABLE online_experiment_report (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, space_id BIGINT NOT NULL, revision INT NOT NULL,
    report_type VARCHAR(16) NOT NULL DEFAULT 'ONLINE_V1', schema_version INT NOT NULL,
    report_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_json LONGTEXT NOT NULL, report_json LONGTEXT NOT NULL,
    observed_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_online_report_revision (experiment_id, revision),
    UNIQUE KEY uk_online_report_input (experiment_id, input_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ONLINE_V1 不可变报告';

-- 旧离线字段值和唯一规则保持；Metric producer 的主体命名空间扩展见下方索引。
-- MySQL 5.7 的 CHECK 不作为约束；P6-01 不开放线上写入口。
ALTER TABLE evaluation_result
    MODIFY run_id BIGINT DEFAULT NULL, MODIFY case_attempt_id BIGINT DEFAULT NULL,
    ADD subject_type VARCHAR(16) NOT NULL DEFAULT 'CASE_ATTEMPT',
    ADD online_assignment_id BIGINT DEFAULT NULL,
    ADD online_evaluation_attempt_id BIGINT DEFAULT NULL,
    ADD task_id BIGINT DEFAULT NULL, ADD execution_id BIGINT DEFAULT NULL,
    ADD UNIQUE KEY uk_evaluation_result_online_attempt (online_evaluation_attempt_id),
    ADD KEY idx_evaluation_result_online (space_id, online_assignment_id, id);

ALTER TABLE evaluation_metric
    MODIFY run_id BIGINT DEFAULT NULL, MODIFY case_run_id BIGINT DEFAULT NULL,
    MODIFY case_attempt_id BIGINT DEFAULT NULL, MODIFY test_case_version_id BIGINT DEFAULT NULL,
    ADD subject_type VARCHAR(16) NOT NULL DEFAULT 'CASE_ATTEMPT',
    ADD online_assignment_id BIGINT DEFAULT NULL,
    ADD online_evaluation_attempt_id BIGINT DEFAULT NULL,
    ADD task_id BIGINT DEFAULT NULL, ADD execution_id BIGINT DEFAULT NULL,
    DROP INDEX uk_evaluation_metric_producer,
    ADD UNIQUE KEY uk_evaluation_metric_producer (subject_type, source, producer_id, metric_key),
    ADD KEY idx_evaluation_metric_online (space_id, online_assignment_id, source, id);

ALTER TABLE evaluation_evidence_reference
    MODIFY case_attempt_id BIGINT DEFAULT NULL,
    ADD subject_type VARCHAR(16) NOT NULL DEFAULT 'CASE_ATTEMPT',
    ADD online_assignment_id BIGINT DEFAULT NULL,
    ADD online_evaluation_attempt_id BIGINT DEFAULT NULL,
    ADD task_id BIGINT DEFAULT NULL, ADD execution_id BIGINT DEFAULT NULL,
    ADD KEY idx_evaluation_evidence_online (space_id, online_assignment_id, id);
