-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V5
-- 说明：document.parent_id 允许 NULL（NULL 表示根目录，替代原 0 约定），存量 0 值迁移为 NULL
-- ============================================================

ALTER TABLE `document`
    MODIFY COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT '父目录 ID，NULL 为根' AFTER `space_id`;

UPDATE `document` SET `parent_id` = NULL WHERE `parent_id` = 0;
