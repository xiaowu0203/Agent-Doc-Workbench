-- Phase 3：Evaluation Core。MySQL 5.7 不使用 CHECK、递归 CTE 或 JSON 类型契约。

CREATE TABLE `evaluation_dataset` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `name` VARCHAR(200) NOT NULL,
    `description` VARCHAR(1000) DEFAULT NULL, `archived` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` BIGINT NOT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_evaluation_dataset_space` (`space_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估数据集';

CREATE TABLE `evaluation_dataset_version` (
    `id` BIGINT NOT NULL, `dataset_id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL,
    `version_no` INT NOT NULL, `status` VARCHAR(16) NOT NULL, `content_hash` VARCHAR(64) DEFAULT NULL,
    `published_at` DATETIME DEFAULT NULL, `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluation_dataset_version` (`dataset_id`, `version_no`),
    KEY `idx_evaluation_dataset_version_space` (`space_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变数据集版本';

CREATE TABLE `evaluation_test_case` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `name` VARCHAR(200) NOT NULL,
    `description` VARCHAR(1000) DEFAULT NULL, `archived` TINYINT(1) NOT NULL DEFAULT 0,
    `created_by` BIGINT NOT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_evaluation_test_case_space` (`space_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估测试用例';

CREATE TABLE `evaluation_test_case_version` (
    `id` BIGINT NOT NULL, `test_case_id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `version_no` INT NOT NULL,
    `status` VARCHAR(16) NOT NULL, `source_task_id` BIGINT NOT NULL, `source_execution_id` BIGINT NOT NULL,
    `source_input_schema_version` INT NOT NULL, `source_input_hash` VARCHAR(64) NOT NULL,
    `source_execution_schema_version` INT NOT NULL, `source_execution_hash` VARCHAR(64) NOT NULL,
    `document_version_snapshot` BIGINT NOT NULL, `document_content_sha256` VARCHAR(64) NOT NULL,
    `expected_schema_version` INT NOT NULL, `expected_json` LONGTEXT NOT NULL,
    `source_type` VARCHAR(32) NOT NULL, `sanitization_note` VARCHAR(1000) DEFAULT NULL,
    `content_hash` VARCHAR(64) DEFAULT NULL, `published_at` DATETIME DEFAULT NULL, `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluation_test_case_version` (`test_case_id`, `version_no`),
    KEY `idx_evaluation_test_case_version_space` (`space_id`, `status`, `id`), KEY `idx_evaluation_test_case_source` (`source_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变测试用例版本';

CREATE TABLE `evaluator` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `name` VARCHAR(200) NOT NULL, `evaluator_key` VARCHAR(64) NOT NULL,
    `description` VARCHAR(1000) DEFAULT NULL, `archived` TINYINT(1) NOT NULL DEFAULT 0, `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`), KEY `idx_evaluator_space` (`space_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间评估器配置';

CREATE TABLE `evaluator_version` (
    `id` BIGINT NOT NULL, `evaluator_id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `version_no` INT NOT NULL,
    `status` VARCHAR(16) NOT NULL, `evaluator_key` VARCHAR(64) NOT NULL, `config_schema_version` INT NOT NULL,
    `config_json` LONGTEXT NOT NULL, `result_schema_version` INT NOT NULL, `implementation_version` VARCHAR(64) NOT NULL,
    `content_hash` VARCHAR(64) DEFAULT NULL, `published_at` DATETIME DEFAULT NULL, `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluator_version` (`evaluator_id`, `version_no`),
    KEY `idx_evaluator_version_space` (`space_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变评估器版本';

CREATE TABLE `evaluation_dataset_case` (
    `id` BIGINT NOT NULL, `dataset_version_id` BIGINT NOT NULL, `test_case_version_id` BIGINT NOT NULL,
    `sort_order` INT NOT NULL, `enabled` TINYINT(1) NOT NULL DEFAULT 1, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluation_dataset_case` (`dataset_version_id`, `test_case_version_id`),
    KEY `idx_evaluation_dataset_case_order` (`dataset_version_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集版本用例绑定';

CREATE TABLE `test_case_evaluator` (
    `id` BIGINT NOT NULL, `test_case_version_id` BIGINT NOT NULL, `evaluator_version_id` BIGINT NOT NULL,
    `expected_json` LONGTEXT DEFAULT NULL, `sort_order` INT NOT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_test_case_evaluator` (`test_case_version_id`, `evaluator_version_id`),
    KEY `idx_test_case_evaluator_order` (`test_case_version_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用例版本评估器绑定';

CREATE TABLE `evaluation_run` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `dataset_version_id` BIGINT DEFAULT NULL,
    `single_test_case_version_id` BIGINT DEFAULT NULL, `status` VARCHAR(32) NOT NULL, `pause_reason` VARCHAR(64) DEFAULT NULL,
    `cancel_requested` TINYINT(1) NOT NULL DEFAULT 0, `case_count` INT NOT NULL,
    `reconciliation_failure_count` INT NOT NULL DEFAULT 0, `created_by` BIGINT NOT NULL,
    `started_at` DATETIME DEFAULT NULL, `finished_at` DATETIME DEFAULT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_evaluation_run_space` (`space_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估运行';

CREATE TABLE `evaluation_case_run` (
    `id` BIGINT NOT NULL, `run_id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `test_case_version_id` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL, `current_attempt_id` BIGINT DEFAULT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluation_run_case` (`run_id`, `test_case_version_id`),
    KEY `idx_evaluation_case_run_status` (`run_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估逻辑用例运行';

CREATE TABLE `evaluation_worker_capability_segment` (
    `id` BIGINT NOT NULL, `run_id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `batch_no` INT NOT NULL,
    `task_set_hash` VARCHAR(64) NOT NULL, `encrypted_capability` LONGTEXT NOT NULL, `key_version` VARCHAR(32) NOT NULL,
    `status` VARCHAR(16) NOT NULL, `expires_at` DATETIME NOT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluation_capability_batch` (`run_id`, `batch_no`),
    KEY `idx_evaluation_capability_status` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估后台窄权限凭证段';

CREATE TABLE `evaluation_case_attempt` (
    `id` BIGINT NOT NULL, `case_run_id` BIGINT NOT NULL, `run_id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL,
    `attempt_no` INT NOT NULL, `replay_task_id` BIGINT DEFAULT NULL, `capability_segment_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL, `failure_stage` VARCHAR(32) DEFAULT NULL, `failure_code` VARCHAR(64) DEFAULT NULL,
    `failure_message` VARCHAR(1000) DEFAULT NULL, `started_at` DATETIME DEFAULT NULL, `finished_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_evaluation_case_attempt` (`case_run_id`, `attempt_no`),
    UNIQUE KEY `uk_evaluation_attempt_replay_task` (`replay_task_id`), KEY `idx_evaluation_attempt_run_status` (`run_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估 Replay 尝试';

CREATE TABLE `evaluation_result` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `run_id` BIGINT NOT NULL, `case_attempt_id` BIGINT NOT NULL,
    `evaluator_version_id` BIGINT NOT NULL, `evaluation_attempt_no` INT NOT NULL, `status` VARCHAR(16) NOT NULL,
    `score` DECIMAL(12,6) DEFAULT NULL, `summary_code` VARCHAR(64) NOT NULL,
    `details_json` LONGTEXT DEFAULT NULL, `implementation_version` VARCHAR(64) NOT NULL, `trace_id` VARCHAR(32) DEFAULT NULL,
    `span_id` VARCHAR(16) DEFAULT NULL, `started_at` DATETIME NOT NULL, `finished_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
    UNIQUE KEY `uk_evaluation_result_attempt` (`case_attempt_id`, `evaluator_version_id`, `evaluation_attempt_no`),
    KEY `idx_evaluation_result_run` (`run_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变评估结果';

CREATE TABLE `evaluation_metric` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `run_id` BIGINT NOT NULL, `case_run_id` BIGINT NOT NULL,
    `case_attempt_id` BIGINT NOT NULL, `test_case_version_id` BIGINT NOT NULL,
    `evaluation_result_id` BIGINT DEFAULT NULL, `evaluator_version_id` BIGINT DEFAULT NULL,
    `contract_version` INT NOT NULL, `source` VARCHAR(16) NOT NULL, `producer_id` BIGINT NOT NULL,
    `metric_key` VARCHAR(128) NOT NULL, `value_type` VARCHAR(16) NOT NULL,
    `numeric_value` DECIMAL(30,10) DEFAULT NULL, `boolean_value` TINYINT(1) DEFAULT NULL,
    `string_value` VARCHAR(191) DEFAULT NULL, `unit` VARCHAR(32) NOT NULL, `direction` VARCHAR(32) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
    UNIQUE KEY `uk_evaluation_metric_producer` (`source`, `producer_id`, `metric_key`),
    KEY `idx_evaluation_metric_run_key` (`run_id`, `metric_key`, `case_run_id`, `id`),
    KEY `idx_evaluation_metric_attempt` (`case_attempt_id`, `source`, `id`),
    KEY `idx_evaluation_metric_result` (`evaluation_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标准化不可变评估业务指标';

CREATE TABLE `evaluation_evidence_reference` (
    `id` BIGINT NOT NULL, `case_attempt_id` BIGINT NOT NULL, `result_id` BIGINT DEFAULT NULL,
    `space_id` BIGINT NOT NULL, `evidence_type` VARCHAR(32) NOT NULL,
    `business_id` VARCHAR(191) NOT NULL, `content_hash` VARCHAR(64) DEFAULT NULL, `summary` VARCHAR(1000) DEFAULT NULL,
    `locator_json` LONGTEXT DEFAULT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_evaluation_evidence_attempt` (`case_attempt_id`, `id`),
    KEY `idx_evaluation_evidence_result` (`result_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评估证据最小引用';

CREATE TABLE `evaluation_metric_evidence` (
    `id` BIGINT NOT NULL, `metric_id` BIGINT NOT NULL, `evidence_reference_id` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
    UNIQUE KEY `uk_evaluation_metric_evidence` (`metric_id`, `evidence_reference_id`),
    KEY `idx_evaluation_metric_evidence_reference` (`evidence_reference_id`, `metric_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标准 Metric 与证据引用关联';

CREATE TABLE `evaluation_feedback` (
    `id` BIGINT NOT NULL, `space_id` BIGINT NOT NULL, `run_id` BIGINT DEFAULT NULL, `case_run_id` BIGINT DEFAULT NULL,
    `task_id` BIGINT DEFAULT NULL, `execution_id` BIGINT DEFAULT NULL, `source_type` VARCHAR(32) NOT NULL,
    `source_business_id` VARCHAR(191) DEFAULT NULL, `source_hash` VARCHAR(64) DEFAULT NULL, `label` VARCHAR(32) NOT NULL,
    `score` DECIMAL(12,6) DEFAULT NULL, `comment` VARCHAR(2000) DEFAULT NULL, `facts_json` VARCHAR(2000) DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
    KEY `idx_evaluation_feedback_space` (`space_id`, `id`),
    UNIQUE KEY `uk_evaluation_feedback_source` (`source_type`, `source_business_id`, `source_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变人工评估反馈';
