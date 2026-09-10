-- Agent 分片写入暂存区：执行完成后一次性提交为一个可见文档版本。
ALTER TABLE `document`
    ADD COLUMN `agent_staged_task_id` BIGINT DEFAULT NULL COMMENT 'Agent 暂存所属任务 ID' AFTER `updated_by`,
    ADD COLUMN `agent_staged_base_version` BIGINT DEFAULT NULL COMMENT 'Agent 暂存基线版本' AFTER `agent_staged_task_id`,
    ADD COLUMN `agent_staged_revision` BIGINT DEFAULT NULL COMMENT 'Agent 暂存内部修订号' AFTER `agent_staged_base_version`,
    ADD COLUMN `agent_staged_content` LONGTEXT DEFAULT NULL COMMENT 'Agent 暂存正文' AFTER `agent_staged_revision`,
    ADD KEY `idx_document_agent_stage_task` (`agent_staged_task_id`);
