-- Phase 6：文档图片附件元数据。
-- 图片二进制存储于 MinIO，本表只保存文档关联、对象 Key 和展示元数据。

CREATE TABLE `document_asset` (
    `id`            BIGINT       NOT NULL COMMENT '附件 ID',
    `document_id`   BIGINT       NOT NULL COMMENT '文档 ID',
    `space_id`      BIGINT       NOT NULL COMMENT '空间 ID',
    `object_key`    VARCHAR(255) NOT NULL COMMENT 'MinIO 对象 Key',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `content_type`  VARCHAR(100) NOT NULL COMMENT '文件 MIME 类型',
    `size_bytes`    BIGINT       NOT NULL COMMENT '文件大小，单位字节',
    `created_by`    BIGINT       DEFAULT NULL COMMENT '上传人用户 ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_asset_object_key` (`object_key`),
    KEY `idx_document_asset_document` (`document_id`),
    KEY `idx_document_asset_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档图片附件元数据';
