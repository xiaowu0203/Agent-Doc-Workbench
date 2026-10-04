-- 线上 schema 2 向前迁移；V30 不变，不启用分配或执行。
-- 先阻止不能自动治理的旧活跃实验/主体冲突；违反时 duplicate key 终止，
-- 部署者应按 ADR-0007 人工治理后再迁移，不删除业务记录。
CREATE TEMPORARY TABLE p6_v31_guard (id INT PRIMARY KEY);
INSERT INTO p6_v31_guard VALUES (1);
INSERT INTO p6_v31_guard SELECT 1 FROM online_experiment
WHERE active_slot IS NOT NULL AND (manifest_schema_version <> 2 OR status NOT IN ('ACTIVE','PAUSED','STOPPING'))
LIMIT 1;
INSERT INTO p6_v31_guard SELECT 1 FROM online_experiment
WHERE active_slot IS NOT NULL GROUP BY space_id HAVING COUNT(*) > 1 LIMIT 1;
INSERT INTO p6_v31_guard SELECT 1 FROM evaluation_result WHERE NOT ((subject_type='CASE_ATTEMPT' AND run_id IS NOT NULL AND case_attempt_id IS NOT NULL AND online_assignment_id IS NULL AND online_evaluation_attempt_id IS NULL AND task_id IS NULL AND execution_id IS NULL) OR (subject_type='ONLINE_TASK' AND run_id IS NULL AND case_attempt_id IS NULL AND online_assignment_id IS NOT NULL AND online_evaluation_attempt_id IS NOT NULL AND task_id IS NOT NULL AND execution_id IS NOT NULL)) LIMIT 1;
INSERT INTO p6_v31_guard SELECT 1 FROM evaluation_metric WHERE NOT ((subject_type='CASE_ATTEMPT' AND run_id IS NOT NULL AND case_run_id IS NOT NULL AND case_attempt_id IS NOT NULL AND test_case_version_id IS NOT NULL AND online_assignment_id IS NULL AND online_evaluation_attempt_id IS NULL AND task_id IS NULL AND execution_id IS NULL) OR (subject_type='ONLINE_TASK' AND run_id IS NULL AND case_run_id IS NULL AND case_attempt_id IS NULL AND test_case_version_id IS NULL AND online_assignment_id IS NOT NULL AND online_evaluation_attempt_id IS NOT NULL AND task_id IS NOT NULL AND execution_id IS NOT NULL)) LIMIT 1;
INSERT INTO p6_v31_guard SELECT 1 FROM evaluation_evidence_reference WHERE NOT ((subject_type='CASE_ATTEMPT' AND case_attempt_id IS NOT NULL AND online_assignment_id IS NULL AND online_evaluation_attempt_id IS NULL AND task_id IS NULL AND execution_id IS NULL) OR (subject_type='ONLINE_TASK' AND case_attempt_id IS NULL AND online_assignment_id IS NOT NULL AND online_evaluation_attempt_id IS NOT NULL AND task_id IS NOT NULL AND execution_id IS NOT NULL)) LIMIT 1;
DROP TEMPORARY TABLE p6_v31_guard;

ALTER TABLE online_experiment
    DROP INDEX uk_online_experiment_active, DROP INDEX uk_online_experiment_request,
    ADD UNIQUE KEY uk_online_experiment_active (space_id, active_slot),
    ADD UNIQUE KEY uk_online_experiment_request (space_id, created_by, client_request_key),
    ADD state_version BIGINT NOT NULL DEFAULT 0,
    ADD accepted_sequence BIGINT NOT NULL DEFAULT 0,
    ADD assignment_deadline DATETIME(3) DEFAULT NULL,
    ADD observation_deadline DATETIME(3) DEFAULT NULL,
    ADD last_reconciled_at DATETIME(3) DEFAULT NULL,
    ADD emergency_stop_requested_at DATETIME(3) DEFAULT NULL,
    ADD baseline_slot_count INT NOT NULL DEFAULT 0,
    ADD candidate_slot_count INT NOT NULL DEFAULT 0,
    ADD unknown_task_count INT NOT NULL DEFAULT 0,
    ADD KEY idx_online_experiment_space_agent_status (space_id, agent_id, status, id);

ALTER TABLE online_assignment
    DROP INDEX uk_online_assignment_request,
    ADD UNIQUE KEY uk_online_assignment_request (space_id, created_by, client_request_key),
    ADD accepted_sequence BIGINT DEFAULT NULL,
    ADD template_id BIGINT DEFAULT NULL,
    ADD dependency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD binding_json LONGTEXT,
    ADD dispatch_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD slot_status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    ADD cancel_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD UNIQUE KEY uk_online_assignment_sequence (experiment_id, accepted_sequence),
    ADD KEY idx_online_assignment_document (experiment_id, document_id, accepted_sequence),
    ADD KEY idx_online_assignment_variant (experiment_id, variant, id);

ALTER TABLE online_evaluation_attempt
    ADD rule_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD expected_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD UNIQUE KEY uk_online_attempt_rule (assignment_id, rule_key, attempt_no);

ALTER TABLE evaluation_feedback
    ADD online_assignment_id BIGINT DEFAULT NULL,
    ADD original_evidence_id BIGINT DEFAULT NULL,
    ADD original_evidence_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD rubric_score TINYINT DEFAULT NULL,
    ADD blindness VARCHAR(16) DEFAULT NULL,
    ADD supersedes_feedback_id BIGINT DEFAULT NULL,
    ADD reason_codes_json TEXT,
    ADD serious_safety_event TINYINT DEFAULT NULL,
    ADD safety_reason VARCHAR(2000) DEFAULT NULL,
    ADD UNIQUE KEY uk_online_feedback_successor (supersedes_feedback_id),
    ADD KEY idx_online_feedback_assignment (online_assignment_id, created_at, id);

CREATE TABLE online_experiment_create_intent (
    id BIGINT NOT NULL, space_id BIGINT NOT NULL, created_by BIGINT NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PREPARING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_online_create_intent (space_id,created_by,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创建意图：id 即预分配实验身份';

CREATE TABLE agent_online_config (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, space_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL, role VARCHAR(16) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version INT NOT NULL, template_json LONGTEXT NOT NULL,
    template_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    non_prompt_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dependency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execution_timeout_seconds INT NOT NULL, max_system_prompt_bytes BIGINT NOT NULL,
    created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_online_template_role (experiment_id,role),
    KEY idx_online_template_space (space_id,agent_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单次捕获当前配置的不可变双模板';

CREATE TABLE task_creation_intent (
    id BIGINT NOT NULL, task_id BIGINT NOT NULL, space_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_budget BIGINT DEFAULT NULL, input_json LONGTEXT, binding_json LONGTEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PREPARING', reason_code VARCHAR(64) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_task_intent_request (space_id,actor_id,request_key),
    UNIQUE KEY uk_task_intent_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新任务幂等解析意图，P6-02 接入';

CREATE TABLE online_execution_slot (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, variant VARCHAR(16) NOT NULL,
    slot_no INT NOT NULL DEFAULT 1, generation BIGINT NOT NULL DEFAULT 0,
    assignment_id BIGINT DEFAULT NULL, task_id BIGINT DEFAULT NULL,
    binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    acquired_at DATETIME(3) DEFAULT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_online_slot_position (experiment_id,variant,slot_no),
    UNIQUE KEY uk_online_slot_task (task_id), UNIQUE KEY uk_online_slot_assignment (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未知不释放的双组权威执行槽';

CREATE TABLE online_experiment_action_request (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_json LONGTEXT, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_online_action_request (experiment_id,action,actor_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='启停/报告/决定请求身份与结果映射';

-- MySQL 5.7 忽略 CHECK；写入与更新都显式拒绝双主体/不完整主体。
DELIMITER $$
CREATE TRIGGER evaluation_result_subject_insert BEFORE INSERT ON evaluation_result
FOR EACH ROW BEGIN
    IF NOT ((NEW.subject_type='CASE_ATTEMPT' AND NEW.run_id IS NOT NULL AND NEW.run_id > 0 AND NEW.case_attempt_id IS NOT NULL AND NEW.case_attempt_id > 0 AND NEW.online_assignment_id IS NULL AND NEW.online_evaluation_attempt_id IS NULL AND NEW.task_id IS NULL AND NEW.execution_id IS NULL) OR (NEW.subject_type='ONLINE_TASK' AND NEW.run_id IS NULL AND NEW.case_attempt_id IS NULL AND NEW.online_assignment_id IS NOT NULL AND NEW.online_assignment_id > 0 AND NEW.online_evaluation_attempt_id IS NOT NULL AND NEW.online_evaluation_attempt_id > 0 AND NEW.task_id IS NOT NULL AND NEW.task_id > 0 AND NEW.execution_id IS NOT NULL AND NEW.execution_id > 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_SUBJECT_INVALID';
    END IF;
END$$
CREATE TRIGGER evaluation_result_subject_update BEFORE UPDATE ON evaluation_result
FOR EACH ROW BEGIN
    IF NOT ((NEW.subject_type='CASE_ATTEMPT' AND NEW.run_id IS NOT NULL AND NEW.run_id > 0 AND NEW.case_attempt_id IS NOT NULL AND NEW.case_attempt_id > 0 AND NEW.online_assignment_id IS NULL AND NEW.online_evaluation_attempt_id IS NULL AND NEW.task_id IS NULL AND NEW.execution_id IS NULL) OR (NEW.subject_type='ONLINE_TASK' AND NEW.run_id IS NULL AND NEW.case_attempt_id IS NULL AND NEW.online_assignment_id IS NOT NULL AND NEW.online_assignment_id > 0 AND NEW.online_evaluation_attempt_id IS NOT NULL AND NEW.online_evaluation_attempt_id > 0 AND NEW.task_id IS NOT NULL AND NEW.task_id > 0 AND NEW.execution_id IS NOT NULL AND NEW.execution_id > 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_SUBJECT_INVALID';
    END IF;
END$$
CREATE TRIGGER evaluation_metric_subject_insert BEFORE INSERT ON evaluation_metric
FOR EACH ROW BEGIN
    IF NOT ((NEW.subject_type='CASE_ATTEMPT' AND NEW.run_id IS NOT NULL AND NEW.run_id > 0 AND NEW.case_run_id IS NOT NULL AND NEW.case_run_id > 0 AND NEW.case_attempt_id IS NOT NULL AND NEW.case_attempt_id > 0 AND NEW.test_case_version_id IS NOT NULL AND NEW.test_case_version_id > 0 AND NEW.online_assignment_id IS NULL AND NEW.online_evaluation_attempt_id IS NULL AND NEW.task_id IS NULL AND NEW.execution_id IS NULL) OR (NEW.subject_type='ONLINE_TASK' AND NEW.run_id IS NULL AND NEW.case_run_id IS NULL AND NEW.case_attempt_id IS NULL AND NEW.test_case_version_id IS NULL AND NEW.online_assignment_id IS NOT NULL AND NEW.online_assignment_id > 0 AND NEW.online_evaluation_attempt_id IS NOT NULL AND NEW.online_evaluation_attempt_id > 0 AND NEW.task_id IS NOT NULL AND NEW.task_id > 0 AND NEW.execution_id IS NOT NULL AND NEW.execution_id > 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_SUBJECT_INVALID';
    END IF;
END$$
CREATE TRIGGER evaluation_metric_subject_update BEFORE UPDATE ON evaluation_metric
FOR EACH ROW BEGIN
    IF NOT ((NEW.subject_type='CASE_ATTEMPT' AND NEW.run_id IS NOT NULL AND NEW.run_id > 0 AND NEW.case_run_id IS NOT NULL AND NEW.case_run_id > 0 AND NEW.case_attempt_id IS NOT NULL AND NEW.case_attempt_id > 0 AND NEW.test_case_version_id IS NOT NULL AND NEW.test_case_version_id > 0 AND NEW.online_assignment_id IS NULL AND NEW.online_evaluation_attempt_id IS NULL AND NEW.task_id IS NULL AND NEW.execution_id IS NULL) OR (NEW.subject_type='ONLINE_TASK' AND NEW.run_id IS NULL AND NEW.case_run_id IS NULL AND NEW.case_attempt_id IS NULL AND NEW.test_case_version_id IS NULL AND NEW.online_assignment_id IS NOT NULL AND NEW.online_assignment_id > 0 AND NEW.online_evaluation_attempt_id IS NOT NULL AND NEW.online_evaluation_attempt_id > 0 AND NEW.task_id IS NOT NULL AND NEW.task_id > 0 AND NEW.execution_id IS NOT NULL AND NEW.execution_id > 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_SUBJECT_INVALID';
    END IF;
END$$
CREATE TRIGGER evaluation_evidence_reference_subject_insert BEFORE INSERT ON evaluation_evidence_reference
FOR EACH ROW BEGIN
    IF NOT ((NEW.subject_type='CASE_ATTEMPT' AND NEW.case_attempt_id IS NOT NULL AND NEW.case_attempt_id > 0 AND NEW.online_assignment_id IS NULL AND NEW.online_evaluation_attempt_id IS NULL AND NEW.task_id IS NULL AND NEW.execution_id IS NULL) OR (NEW.subject_type='ONLINE_TASK' AND NEW.case_attempt_id IS NULL AND NEW.online_assignment_id IS NOT NULL AND NEW.online_assignment_id > 0 AND NEW.online_evaluation_attempt_id IS NOT NULL AND NEW.online_evaluation_attempt_id > 0 AND NEW.task_id IS NOT NULL AND NEW.task_id > 0 AND NEW.execution_id IS NOT NULL AND NEW.execution_id > 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_SUBJECT_INVALID';
    END IF;
END$$
CREATE TRIGGER evaluation_evidence_reference_subject_update BEFORE UPDATE ON evaluation_evidence_reference
FOR EACH ROW BEGIN
    IF NOT ((NEW.subject_type='CASE_ATTEMPT' AND NEW.case_attempt_id IS NOT NULL AND NEW.case_attempt_id > 0 AND NEW.online_assignment_id IS NULL AND NEW.online_evaluation_attempt_id IS NULL AND NEW.task_id IS NULL AND NEW.execution_id IS NULL) OR (NEW.subject_type='ONLINE_TASK' AND NEW.case_attempt_id IS NULL AND NEW.online_assignment_id IS NOT NULL AND NEW.online_assignment_id > 0 AND NEW.online_evaluation_attempt_id IS NOT NULL AND NEW.online_evaluation_attempt_id > 0 AND NEW.task_id IS NOT NULL AND NEW.task_id > 0 AND NEW.execution_id IS NOT NULL AND NEW.execution_id > 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_SUBJECT_INVALID';
    END IF;
END$$
DELIMITER ;
