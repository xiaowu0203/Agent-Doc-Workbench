ALTER TABLE `agent_execution`
    ADD COLUMN `space_id` BIGINT DEFAULT NULL
        COMMENT '执行所属空间 ID 快照' AFTER `workbench_task_id`,
    ADD COLUMN `agent_name_snapshot` VARCHAR(100) DEFAULT NULL
        COMMENT '执行时 Agent 名称快照' AFTER `agent_id`,
    ADD COLUMN `max_iterations` INT DEFAULT NULL
        COMMENT '执行时最大模型迭代次数' AFTER `agent_config_version`,
    ADD COLUMN `execution_timeout_seconds` INT DEFAULT NULL
        COMMENT '执行时超时秒数' AFTER `max_iterations`,
    ADD COLUMN `model_config_version` BIGINT DEFAULT NULL
        COMMENT '执行时模型配置版本' AFTER `model_snapshot`,
    ADD COLUMN `model_display_name_snapshot` VARCHAR(100) DEFAULT NULL
        COMMENT '执行时模型展示名称快照' AFTER `model_config_version`,
    ADD COLUMN `execution_snapshot_hash` VARCHAR(64) DEFAULT NULL
        COMMENT '执行上下文稳定快照 SHA-256' AFTER `prompt_hash`,
    ADD KEY `idx_agent_execution_space_task` (`space_id`, `workbench_task_id`);

ALTER TABLE `agent_execution_tool_call`
    ADD COLUMN `skill_version_id` BIGINT DEFAULT NULL
        COMMENT 'Skill 本地读取工具指向的版本 ID' AFTER `mcp_server_id`,
    ADD KEY `idx_execution_skill_version` (`execution_id`, `skill_version_id`);

UPDATE `agent_execution` execution
JOIN `task` task_record ON task_record.`id` = execution.`workbench_task_id`
SET execution.`space_id` = task_record.`space_id`
WHERE execution.`space_id` IS NULL;
