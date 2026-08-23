-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V4
-- 说明：change_request 新增 base_version 列（审批合并时校验基线版本，防止并发覆盖）
-- ============================================================

ALTER TABLE `change_request`
    ADD COLUMN `base_version` BIGINT NOT NULL DEFAULT 0 COMMENT '目标文档基线版本号（合并时校验防并发覆盖）' AFTER `changes`;
