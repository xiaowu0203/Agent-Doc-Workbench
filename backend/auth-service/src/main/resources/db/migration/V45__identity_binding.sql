-- 外部身份只绑定既有业务用户；撤销后保留唯一键与历史，不允许静默重绑。
CREATE TABLE `identity_binding` (
    `id` BIGINT NOT NULL COMMENT '雪花 ID',
    `identity_key` BINARY(32) NOT NULL COMMENT 'issuer/sub 的 v1 SHA-256 摘要',
    `issuer` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '精确 issuer',
    `subject` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '精确 subject',
    `user_id` BIGINT NOT NULL COMMENT '已有业务用户 ID',
    `status` VARCHAR(16) NOT NULL COMMENT 'ACTIVE / REVOKED',
    `bound_by` BIGINT NOT NULL COMMENT '绑定操作者',
    `bound_at` DATETIME NOT NULL COMMENT '绑定时间',
    `revoked_by` BIGINT DEFAULT NULL COMMENT '撤销操作者',
    `revoked_at` DATETIME DEFAULT NULL COMMENT '撤销时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_binding_key` (`identity_key`),
    KEY `idx_identity_binding_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部身份与业务用户的不可重绑映射';
