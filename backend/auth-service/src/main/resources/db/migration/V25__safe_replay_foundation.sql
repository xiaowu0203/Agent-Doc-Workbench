-- Phase 3：Safe Replay、不可变执行产物和评估权限基础。

ALTER TABLE `agent_execution`
    ADD COLUMN `execution_snapshot_json` LONGTEXT DEFAULT NULL
        COMMENT 'schema v3 完整非秘密执行快照' AFTER `execution_snapshot_schema_version`;

ALTER TABLE `task`
    ADD COLUMN `derivation_request_key` VARCHAR(191) DEFAULT NULL
        COMMENT '派生任务全局幂等键' AFTER `execution_mode`,
    ADD COLUMN `derivation_request_hash` VARCHAR(64) DEFAULT NULL
        COMMENT '派生请求稳定 SHA-256' AFTER `derivation_request_key`,
    ADD UNIQUE KEY `uk_task_derivation_request_key` (`derivation_request_key`);

CREATE TABLE `execution_artifact` (
    `id` BIGINT NOT NULL COMMENT 'Artifact ID',
    `space_id` BIGINT NOT NULL COMMENT '所属空间 ID',
    `task_id` BIGINT NOT NULL COMMENT '隔离 Task ID',
    `execution_id` BIGINT NOT NULL COMMENT 'AgentExecution ID',
    `source_task_id` BIGINT NOT NULL COMMENT 'Replay 来源 Task ID',
    `sequence_no` INT NOT NULL COMMENT '执行内稳定序号',
    `source_tool_call_id` BIGINT DEFAULT NULL COMMENT '来源工具调用审计 ID',
    `artifact_type` VARCHAR(32) NOT NULL COMMENT 'CHANGE_PROPOSAL / DRAFT_CHANGES / RESULT_SUMMARY',
    `schema_version` INT NOT NULL COMMENT 'payload schema 版本',
    `payload_json` LONGTEXT NOT NULL COMMENT '结构化候选内容',
    `payload_sha256` VARCHAR(64) NOT NULL COMMENT 'payload 稳定 SHA-256',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution_artifact_sequence` (`execution_id`, `sequence_no`),
    KEY `idx_execution_artifact_task` (`task_id`, `id`),
    KEY `idx_execution_artifact_space` (`space_id`, `id`),
    KEY `idx_execution_artifact_source` (`source_task_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='隔离执行不可变候选产物';

INSERT INTO `permission` (`code`, `name`, `category`, `description`, `sort_order`) VALUES
('evaluation:read', '查看评估', 'EVALUATION', '查看数据集、评估运行、结果和反馈', 280),
('evaluation:manage', '管理评估', 'EVALUATION', '管理数据集、测试用例和评估器版本', 290),
('evaluation:run', '运行评估', 'EVALUATION', '创建 Replay、运行和重试评估、提交人工反馈', 300);

INSERT INTO `space_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`code`
FROM `space_role` r
JOIN `permission` p ON p.`code` IN ('evaluation:read', 'evaluation:manage', 'evaluation:run')
WHERE r.`role_key` IN ('OWNER', 'EDITOR');

INSERT INTO `space_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'evaluation:read'
FROM `space_role` r
WHERE r.`role_key` = 'VIEWER';
