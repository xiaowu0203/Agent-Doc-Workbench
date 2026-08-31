-- ============================================================
-- Phase 5：平台超级管理员 + 空间级 RBAC
-- MySQL 5.7 兼容；V1 基线不可修改。
-- ============================================================

CREATE TABLE `platform_role` (
    `id`             BIGINT       NOT NULL COMMENT '角色 ID',
    `role_key`       VARCHAR(64)  NOT NULL COMMENT '稳定技术标识',
    `display_name`   VARCHAR(100) NOT NULL COMMENT '展示名称',
    `protected_role` TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否为受保护平台角色',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台角色';

CREATE TABLE `user_platform_role` (
    `id`         BIGINT   NOT NULL COMMENT '雪花 ID',
    `user_id`    BIGINT   NOT NULL COMMENT '用户 ID',
    `role_id`    BIGINT   NOT NULL COMMENT '平台角色 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_platform_role` (`user_id`, `role_id`),
    KEY `idx_user_platform_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户平台角色绑定';

INSERT INTO `platform_role` (`id`, `role_key`, `display_name`, `protected_role`)
VALUES (1, 'PLATFORM_SUPER_ADMIN', '平台超级管理员', 1);

CREATE TABLE `permission` (
    `code`        VARCHAR(64)  NOT NULL COMMENT '稳定权限标识符',
    `name`        VARCHAR(100) NOT NULL COMMENT '展示名称',
    `category`    VARCHAR(32)  NOT NULL COMMENT '权限分类',
    `description` VARCHAR(255) NOT NULL COMMENT '权限说明',
    `sort_order`  INT          NOT NULL COMMENT '展示顺序',
    PRIMARY KEY (`code`),
    KEY `idx_permission_category_sort` (`category`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间权限目录';

INSERT INTO `permission` (`code`, `name`, `category`, `description`, `sort_order`) VALUES
('space:read', '查看空间', 'SPACE', '查看空间基础信息', 10),
('space:manage', '管理空间', 'SPACE', '修改空间配置和预算', 20),
('space:delete', '删除空间', 'SPACE', '删除空间', 30),
('member:read', '查看成员', 'MEMBER', '查看空间成员列表', 40),
('member:manage', '管理成员', 'MEMBER', '添加、移除成员和分配空间角色', 50),
('role:read', '查看角色', 'ROLE', '查看空间角色及权限', 60),
('role:manage', '管理角色', 'ROLE', '创建、修改和删除自定义角色', 70),
('document:read', '查看文档', 'DOCUMENT', '查看文档、版本和回收站', 80),
('document:create', '创建文档', 'DOCUMENT', '在空间中创建文档', 90),
('document:edit', '编辑文档', 'DOCUMENT', '编辑、移动、归档、恢复和回滚文档', 100),
('agent:read', '查看 Agent', 'AGENT', '查看 Agent 配置', 110),
('agent:manage', '管理 Agent', 'AGENT', '创建、修改和删除 Agent', 120),
('agent:bind_skill', '绑定 Skill', 'AGENT', '修改 Agent 的 Skill 绑定', 130),
('agent:bind_mcp', '绑定 MCP', 'AGENT', '修改 Agent 的外部 MCP 绑定', 140),
('skill:read', '查看 Skill', 'SKILL', '查看和下载 Skill 及版本', 150),
('skill:manage', '管理 Skill', 'SKILL', '创建、修改、上传、发布和启停 Skill', 160),
('mcp:read', '查看 MCP', 'MCP', '查看空间外部 MCP 配置', 170),
('mcp:manage', '管理 MCP', 'MCP', '创建、修改和删除空间外部 MCP 配置', 180),
('task:read', '查看任务', 'TASK', '查看空间任务', 190),
('task:create', '创建任务', 'TASK', '创建 Agent 任务', 200),
('task:terminate', '终止任务', 'TASK', '终止 Agent 任务', 210),
('change_request:read', '查看变更请求', 'CHANGE_REQUEST', '查看空间变更请求', 220),
('change_request:submit', '提交变更请求', 'CHANGE_REQUEST', '以人类身份提交文档变更请求', 230),
('change_request:approve', '审批变更请求', 'CHANGE_REQUEST', '通过、拒绝或退回变更请求', 240),
('change_request:merge', '合并变更请求', 'CHANGE_REQUEST', '将已通过变更合并到正式文档', 250),
('usage:read', '查看用量', 'USAGE', '查看空间 Token 用量', 260),
('audit:read', '查看审计', 'AUDIT', '查看空间审计日志', 270);

CREATE TABLE `space_role` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色 ID；应用新增使用雪花 ID',
    `space_id`       BIGINT       NOT NULL COMMENT '所属空间 ID',
    `role_key`       VARCHAR(64)  NOT NULL COMMENT '空间内稳定技术标识',
    `display_name`   VARCHAR(100) NOT NULL COMMENT '展示名称',
    `description`    VARCHAR(255) DEFAULT NULL COMMENT '角色说明',
    `protected_role` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为受保护默认角色',
    `created_by`     BIGINT       DEFAULT NULL COMMENT '创建人用户 ID',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_role_key` (`space_id`, `role_key`),
    KEY `idx_space_role_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间角色';

CREATE TABLE `space_role_permission` (
    `role_id`         BIGINT      NOT NULL COMMENT '空间角色 ID',
    `permission_code` VARCHAR(64) NOT NULL COMMENT '权限标识符',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (`role_id`, `permission_code`),
    KEY `idx_role_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间角色权限绑定';

INSERT INTO `space_role` (`space_id`, `role_key`, `display_name`, `description`, `protected_role`, `created_by`)
SELECT s.`id`, defaults.`role_key`, defaults.`display_name`, defaults.`description`, 1, s.`owner_id`
FROM `space` s
CROSS JOIN (
    SELECT 'OWNER' AS `role_key`, '所有者' AS `display_name`, '拥有空间全部权限' AS `description`
    UNION ALL SELECT 'EDITOR', '编辑者', '可编辑文档、创建任务和审批变更'
    UNION ALL SELECT 'VIEWER', '观察者', '只读查看空间资源'
) defaults
WHERE s.`deleted` = 0;

INSERT INTO `space_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`code`
FROM `space_role` r
CROSS JOIN `permission` p
WHERE r.`role_key` = 'OWNER';

INSERT INTO `space_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`code`
FROM `space_role` r
JOIN `permission` p ON p.`code` IN (
    'space:read', 'member:read', 'role:read', 'document:read', 'document:create', 'document:edit',
    'agent:read', 'skill:read', 'mcp:read', 'task:read', 'task:create', 'task:terminate',
    'change_request:read', 'change_request:submit', 'change_request:approve', 'change_request:merge',
    'usage:read', 'audit:read'
)
WHERE r.`role_key` = 'EDITOR';

INSERT INTO `space_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, p.`code`
FROM `space_role` r
JOIN `permission` p ON p.`code` IN (
    'space:read', 'member:read', 'role:read', 'document:read', 'agent:read', 'skill:read',
    'mcp:read', 'task:read', 'change_request:read', 'usage:read', 'audit:read'
)
WHERE r.`role_key` = 'VIEWER';

ALTER TABLE `member`
    ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT '空间角色 ID' AFTER `user_id`;

UPDATE `member` m
JOIN `space_role` r
  ON r.`space_id` = m.`space_id`
 AND r.`role_key` = CASE m.`role`
     WHEN 1 THEN 'OWNER'
     WHEN 2 THEN 'EDITOR'
     WHEN 3 THEN 'VIEWER'
 END
SET m.`role_id` = r.`id`;

ALTER TABLE `member`
    MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '空间角色 ID',
    DROP COLUMN `role`,
    ADD KEY `idx_member_role` (`role_id`);
