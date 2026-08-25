-- Agent Service、A2A 任务映射与 MCP 远程执行所需结构。
ALTER TABLE `agent`
    ADD COLUMN `system_prompt` TEXT DEFAULT NULL COMMENT '用户可配置的 Agent 系统提示词' AFTER `description`,
    ADD COLUMN `config_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Agent 配置版本' AFTER `token_budget`,
    ADD COLUMN `max_iterations` INT NOT NULL DEFAULT 12 COMMENT '单次执行最大模型迭代次数' AFTER `config_version`,
    ADD COLUMN `execution_timeout_seconds` INT NOT NULL DEFAULT 600 COMMENT '单次执行超时秒数' AFTER `max_iterations`;

ALTER TABLE `model`
    ADD COLUMN `base_url` VARCHAR(500) DEFAULT NULL COMMENT 'OpenAI 兼容 API 地址' AFTER `official_url`,
    ADD COLUMN `encrypted_api_key` TEXT DEFAULT NULL COMMENT 'AES-GCM 加密后的模型 API Key' AFTER `base_url`;

CREATE TABLE `agent_execution` (
    `id`                     BIGINT       NOT NULL COMMENT '雪花 ID',
    `a2a_task_id`            VARCHAR(100) NOT NULL COMMENT 'A2A 标准任务 ID',
    `a2a_context_id`         VARCHAR(100) NOT NULL COMMENT 'A2A 标准上下文 ID',
    `workbench_task_id`      BIGINT       NOT NULL COMMENT 'Workbench Task ID，幂等键',
    `agent_id`               BIGINT       NOT NULL COMMENT 'Agent ID',
    `agent_config_version`   BIGINT       NOT NULL COMMENT '执行时 Agent 配置版本',
    `system_prompt_snapshot` LONGTEXT     NOT NULL COMMENT '执行时系统提示词快照',
    `model_snapshot`         TEXT         NOT NULL COMMENT '脱敏模型配置快照',
    `prompt_hash`            VARCHAR(64)  NOT NULL COMMENT '完整提示词 SHA-256',
    `status`                 VARCHAR(32)  NOT NULL COMMENT 'A2A 执行状态',
    `cancel_requested`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否请求取消',
    `input_tokens`           BIGINT       NOT NULL DEFAULT 0 COMMENT '输入 Token',
    `cached_input_tokens`    BIGINT       DEFAULT NULL COMMENT '缓存输入 Token',
    `output_tokens`          BIGINT       NOT NULL DEFAULT 0 COMMENT '输出 Token',
    `result_summary`         TEXT         DEFAULT NULL COMMENT '执行结果摘要',
    `error_message`          VARCHAR(2000) DEFAULT NULL COMMENT '失败原因',
    `started_at`             DATETIME     DEFAULT NULL COMMENT '开始时间',
    `finished_at`            DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_execution_a2a_task` (`a2a_task_id`),
    KEY `idx_agent_execution_context` (`a2a_context_id`),
    UNIQUE KEY `uk_agent_execution_workbench_task` (`workbench_task_id`),
    KEY `idx_agent_execution_agent_status` (`agent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent A2A 执行记录';

ALTER TABLE `task`
    ADD COLUMN `agent_config_version` BIGINT DEFAULT NULL COMMENT '执行时 Agent 配置版本' AFTER `agent_id`,
    ADD COLUMN `agent_execution_id` BIGINT DEFAULT NULL COMMENT 'Agent Service 执行 ID' AFTER `agent_config_version`,
    ADD COLUMN `a2a_task_id` VARCHAR(100) DEFAULT NULL COMMENT 'A2A 标准任务 ID' AFTER `agent_execution_id`,
    ADD COLUMN `a2a_context_id` VARCHAR(100) DEFAULT NULL COMMENT 'A2A 上下文 ID' AFTER `a2a_task_id`,
    ADD COLUMN `prompt_hash` VARCHAR(64) DEFAULT NULL COMMENT '执行提示词 SHA-256' AFTER `a2a_context_id`,
    ADD COLUMN `dispatched_at` DATETIME DEFAULT NULL COMMENT 'A2A 分发时间' AFTER `start_time`,
    ADD COLUMN `last_heartbeat_at` DATETIME DEFAULT NULL COMMENT 'A2A 最近状态时间' AFTER `dispatched_at`,
    ADD UNIQUE KEY `uk_task_a2a_task` (`a2a_task_id`),
    ADD KEY `idx_task_heartbeat` (`status`, `last_heartbeat_at`);
