-- ============================================================
-- 平台用户与部门管理
-- 部门仅表达组织归属，不参与 Space 权限计算。
-- ============================================================

CREATE TABLE `department` (
    `id`             BIGINT       NOT NULL COMMENT '雪花 ID',
    `parent_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '上级部门 ID，0 为组织根节点',
    `name`           VARCHAR(100) NOT NULL COMMENT '部门名称',
    `code`           VARCHAR(64)  NOT NULL COMMENT '稳定部门编码',
    `leader_user_id` BIGINT       DEFAULT NULL COMMENT '负责人用户 ID',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '同级排序值',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_department_code` (`code`),
    KEY `idx_department_parent_sort` (`parent_id`, `sort_order`),
    KEY `idx_department_leader` (`leader_user_id`),
    KEY `idx_department_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台组织部门';

ALTER TABLE `user`
    ADD COLUMN `department_id` BIGINT DEFAULT NULL COMMENT '所属部门 ID' AFTER `avatar_url`,
    ADD COLUMN `job_title` VARCHAR(100) DEFAULT NULL COMMENT '职位' AFTER `department_id`,
    ADD COLUMN `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间' AFTER `status`,
    ADD KEY `idx_user_department` (`department_id`),
    ADD KEY `idx_user_status` (`status`),
    ADD KEY `idx_user_last_login` (`last_login_at`);
