-- Phase 6：文档树支持独立目录节点。
-- 现有记录统一视为普通文档，新增目录使用 node_type=2。

ALTER TABLE `document`
    ADD COLUMN `node_type` TINYINT NOT NULL DEFAULT 1 COMMENT '节点类型：1 文档 / 2 目录' AFTER `doc_type`;
