-- Phase 6：目录作为独立资源，文档表只保存文档。
-- 旧 document.parent_id / document.node_type 列暂时保留，作为兼容存量数据的遗留列；业务实体不再映射或使用它们。

CREATE TABLE `document_directory` (
    `id`          BIGINT        NOT NULL COMMENT '目录 ID',
    `space_id`    BIGINT        NOT NULL COMMENT '所属空间 ID',
    `parent_id`   BIGINT        DEFAULT NULL COMMENT '父目录 ID，NULL 为根目录',
    `title`       VARCHAR(200)  NOT NULL COMMENT '目录名称',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 归档',
    `created_by`  BIGINT        DEFAULT NULL COMMENT '创建人用户 ID',
    `updated_by`  BIGINT        DEFAULT NULL COMMENT '最后更新人用户 ID',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_doc_directory_space_parent` (`space_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档目录表';

ALTER TABLE `document`
    ADD COLUMN `directory_id` BIGINT DEFAULT NULL COMMENT '所属目录 ID；NULL 表示空间根层' AFTER `space_id`;

CREATE INDEX `idx_doc_space_directory` ON `document` (`space_id`, `directory_id`);
