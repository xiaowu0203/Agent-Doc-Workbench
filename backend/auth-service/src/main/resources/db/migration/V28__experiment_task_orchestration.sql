-- Phase 4 P4-03：Experiment Task 候选配置身份与 EvaluationRun 幂等关联。
-- V27 已提交，不回改历史迁移；MySQL 5.7 不使用 CHECK 或 JSON 类型。

ALTER TABLE `task`
    ADD COLUMN `candidate_config_id` BIGINT DEFAULT NULL
        COMMENT 'Experiment 使用的不可变 Agent 候选配置 ID' AFTER `derivation_request_hash`,
    ADD COLUMN `candidate_snapshot_schema_version` INT DEFAULT NULL
        COMMENT '候选执行快照 schema 版本' AFTER `candidate_config_id`,
    ADD COLUMN `candidate_snapshot_hash` VARCHAR(64) DEFAULT NULL
        COMMENT '候选执行快照 SHA-256' AFTER `candidate_snapshot_schema_version`,
    ADD KEY `idx_task_candidate_config` (`candidate_config_id`, `id`);

ALTER TABLE `evaluation_run`
    ADD COLUMN `experiment_variant_id` BIGINT DEFAULT NULL
        COMMENT 'Experiment Variant ID；普通 EvaluationRun 为空' AFTER `single_test_case_version_id`,
    ADD UNIQUE KEY `uk_evaluation_run_experiment_variant` (`experiment_variant_id`);
