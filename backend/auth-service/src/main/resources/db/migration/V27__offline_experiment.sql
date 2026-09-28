-- Phase 4：Offline Experiment 数据模型与通用执行 Task 关联。
-- MySQL 5.7 不使用 CHECK、递归 CTE 或 JSON 类型契约。

ALTER TABLE `evaluation_case_attempt`
    ADD COLUMN `execution_task_id` BIGINT DEFAULT NULL
        COMMENT 'Replay 或 Experiment 的执行 Task ID' AFTER `replay_task_id`;

UPDATE `evaluation_case_attempt`
SET `execution_task_id` = `replay_task_id`
WHERE `replay_task_id` IS NOT NULL
  AND `execution_task_id` IS NULL;

ALTER TABLE `evaluation_case_attempt`
    ADD UNIQUE KEY `uk_evaluation_attempt_execution_task` (`execution_task_id`);

CREATE TABLE `agent_candidate_config` (
    `id` BIGINT NOT NULL COMMENT '候选配置 ID',
    `space_id` BIGINT NOT NULL COMMENT '所属空间 ID',
    `agent_id` BIGINT NOT NULL COMMENT '来源 Agent ID',
    `source_task_id` BIGINT NOT NULL COMMENT '来源 Task ID',
    `source_execution_id` BIGINT NOT NULL COMMENT '来源 AgentExecution ID',
    `request_key` VARCHAR(191) NOT NULL COMMENT '候选配置创建幂等键',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '候选配置创建请求 SHA-256',
    `source_snapshot_schema_version` INT NOT NULL COMMENT '来源执行快照 schema 版本',
    `source_snapshot_hash` VARCHAR(64) NOT NULL COMMENT '来源执行快照 SHA-256',
    `candidate_snapshot_schema_version` INT NOT NULL COMMENT '候选执行快照 schema 版本',
    `candidate_snapshot_hash` VARCHAR(64) NOT NULL COMMENT '候选执行快照 SHA-256',
    `snapshot_without_prompt_hash` VARCHAR(64) NOT NULL COMMENT '移除 systemPrompt 后的公共快照 SHA-256',
    `prompt_diff_field_paths` LONGTEXT NOT NULL COMMENT '实际 Prompt 差异字段路径 JSON',
    `agent_prompt` LONGTEXT NOT NULL COMMENT '候选 Agent Prompt 正文',
    `system_prompt` LONGTEXT NOT NULL COMMENT '统一构建后的最终 system prompt',
    `prompt_hash` VARCHAR(64) NOT NULL COMMENT '候选 Prompt 派生哈希',
    `execution_snapshot_json` LONGTEXT NOT NULL COMMENT '候选 canonical execution snapshot v3',
    `created_by` BIGINT NOT NULL COMMENT '创建人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_candidate_config_request` (`request_key`),
    KEY `idx_agent_candidate_config_space_agent` (`space_id`, `agent_id`, `id`),
    KEY `idx_agent_candidate_config_source` (`source_execution_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 不可变候选执行配置';

CREATE TABLE `experiment` (
    `id` BIGINT NOT NULL COMMENT 'Experiment ID',
    `space_id` BIGINT NOT NULL COMMENT '所属空间 ID',
    `dataset_version_id` BIGINT NOT NULL COMMENT '冻结的数据集版本 ID',
    `status` VARCHAR(32) NOT NULL COMMENT 'Experiment 状态',
    `client_request_key` VARCHAR(191) NOT NULL COMMENT '客户端创建幂等键',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '创建请求 SHA-256',
    `manifest_schema_version` INT NOT NULL COMMENT 'manifest schema 版本',
    `manifest_json` LONGTEXT NOT NULL COMMENT '冻结 manifest JSON',
    `manifest_hash` VARCHAR(64) NOT NULL COMMENT 'manifest SHA-256',
    `authorized_token_budget` BIGINT DEFAULT NULL COMMENT '启动时确认的计划 Token 授权上限',
    `failure_code` VARCHAR(64) DEFAULT NULL COMMENT '稳定失败码',
    `failure_message` VARCHAR(1000) DEFAULT NULL COMMENT '脱敏失败说明',
    `created_by` BIGINT NOT NULL COMMENT '创建人',
    `started_by` BIGINT DEFAULT NULL COMMENT '启动人',
    `started_at` DATETIME DEFAULT NULL COMMENT '启动时间',
    `cancel_requested_by` BIGINT DEFAULT NULL COMMENT '取消请求人',
    `cancel_requested_at` DATETIME DEFAULT NULL COMMENT '取消请求时间',
    `decision` VARCHAR(32) DEFAULT NULL COMMENT '人工结论',
    `decision_reason` VARCHAR(1000) DEFAULT NULL COMMENT '人工结论理由',
    `decision_report_revision` INT DEFAULT NULL COMMENT '人工结论引用的报告 revision',
    `decided_by` BIGINT DEFAULT NULL COMMENT '决策人',
    `decided_at` DATETIME DEFAULT NULL COMMENT '决策时间',
    `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_request` (`space_id`, `client_request_key`),
    KEY `idx_experiment_space_status` (`space_id`, `status`, `id`),
    KEY `idx_experiment_dataset` (`dataset_version_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线实验';

CREATE TABLE `experiment_variant` (
    `id` BIGINT NOT NULL COMMENT 'Variant ID',
    `experiment_id` BIGINT NOT NULL COMMENT 'Experiment ID',
    `space_id` BIGINT NOT NULL COMMENT '所属空间 ID',
    `variant_key` VARCHAR(64) NOT NULL COMMENT 'Experiment 内稳定 Variant key',
    `role` VARCHAR(16) NOT NULL COMMENT 'BASELINE / CANDIDATE',
    `variant_type` VARCHAR(32) NOT NULL COMMENT '首版仅 PROMPT',
    `candidate_config_id` BIGINT DEFAULT NULL COMMENT 'Agent 候选配置 ID；baseline 为空',
    `source_snapshot_schema_version` INT NOT NULL COMMENT '来源执行快照 schema 版本',
    `source_snapshot_hash` VARCHAR(64) NOT NULL COMMENT '来源执行快照 SHA-256',
    `candidate_snapshot_schema_version` INT NOT NULL COMMENT '实际候选执行快照 schema 版本',
    `candidate_snapshot_hash` VARCHAR(64) NOT NULL COMMENT '实际候选执行快照 SHA-256',
    `prompt_diff_field_paths` LONGTEXT NOT NULL COMMENT 'Prompt 差异字段路径 JSON',
    `snapshot_without_prompt_hash` VARCHAR(64) NOT NULL COMMENT '移除 systemPrompt 后的公共快照 SHA-256',
    `evaluation_run_id` BIGINT DEFAULT NULL COMMENT '关联 EvaluationRun ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_variant_key` (`experiment_id`, `variant_key`),
    UNIQUE KEY `uk_experiment_variant_run` (`evaluation_run_id`),
    KEY `idx_experiment_variant_list` (`experiment_id`, `id`),
    KEY `idx_experiment_variant_candidate_config` (`candidate_config_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线实验不可变变体';

CREATE TABLE `experiment_report` (
    `id` BIGINT NOT NULL COMMENT 'Report ID',
    `experiment_id` BIGINT NOT NULL COMMENT 'Experiment ID',
    `space_id` BIGINT NOT NULL COMMENT '所属空间 ID',
    `revision` INT NOT NULL COMMENT 'Experiment 内单调递增修订号',
    `manifest_hash` VARCHAR(64) NOT NULL COMMENT '报告使用的 manifest SHA-256',
    `calculation_schema_version` INT NOT NULL COMMENT '报告计算 schema 版本',
    `calculation_input_hash` VARCHAR(64) NOT NULL COMMENT '选中记录及计算输入 SHA-256',
    `selected_record_ids_json` LONGTEXT NOT NULL COMMENT '选中的 Run/Attempt/Result/Metric/Feedback ID JSON',
    `report_schema_version` INT NOT NULL COMMENT '报告内容 schema 版本',
    `report_json` LONGTEXT NOT NULL COMMENT '逐用例与聚合报告 JSON',
    `content_hash` VARCHAR(64) NOT NULL COMMENT '报告内容 SHA-256',
    `recalculation_request_key` VARCHAR(191) DEFAULT NULL COMMENT '显式重算幂等键；自动首版为空',
    `recalculation_request_hash` VARCHAR(64) DEFAULT NULL COMMENT '显式重算请求 SHA-256',
    `generated_by` BIGINT NOT NULL COMMENT '生成或重算主体',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_report_revision` (`experiment_id`, `revision`),
    UNIQUE KEY `uk_experiment_report_input` (`experiment_id`, `calculation_input_hash`),
    UNIQUE KEY `uk_experiment_report_recalculation` (`experiment_id`, `recalculation_request_key`),
    KEY `idx_experiment_report_space` (`space_id`, `experiment_id`, `revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变离线实验报告';
