-- Phase 3：任务执行、Agent 结果和审计追踪所需的增量字段。
ALTER TABLE `task`
    ADD COLUMN `name` VARCHAR(100) DEFAULT NULL COMMENT '任务名称' AFTER `document_id`,
    ADD COLUMN `error_message` VARCHAR(2000) DEFAULT NULL COMMENT '最近一次失败原因' AFTER `end_time`,
    ADD COLUMN `result_summary` TEXT DEFAULT NULL COMMENT '任务结果摘要' AFTER `error_message`,
    ADD COLUMN `parent_task_id` BIGINT DEFAULT NULL COMMENT '预留父任务 ID，Phase 3 不参与业务逻辑' AFTER `result_summary`,
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '消息重试次数' AFTER `parent_task_id`,
    ADD COLUMN `capability_token` TEXT DEFAULT NULL COMMENT '加密保存的任务能力令牌' AFTER `retry_count`,
    ADD KEY `idx_task_space_status` (`space_id`, `status`);

ALTER TABLE `change_request`
    ADD COLUMN `proposed_actor_type` TINYINT NOT NULL DEFAULT 1 COMMENT '提交主体：1 人 / 2 Agent' AFTER `proposed_by`;

ALTER TABLE `audit_log`
    ADD COLUMN `task_id` BIGINT DEFAULT NULL COMMENT '关联任务 ID' AFTER `space_id`,
    ADD KEY `idx_audit_task` (`task_id`);

-- 审计日志在数据库层保持追加-only，应用层也不提供更新/删除能力。
CREATE TRIGGER `trg_audit_log_no_update`
BEFORE UPDATE ON `audit_log`
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is insert-only';

CREATE TRIGGER `trg_audit_log_no_delete`
BEFORE DELETE ON `audit_log`
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is insert-only';
