-- ============================================================
-- Agent-Doc-Workbench 数据库初始化脚本 V1
-- 说明：v0.1 采用单一数据库 agent_doc_workbench，由 auth-service
--       统一托管 Flyway 迁移。所有表使用 InnoDB + utf8mb4 + 雪花 ID + 逻辑删除。
-- MySQL 5.7 兼容：DATETIME，不依赖 MySQL 8 特性。
-- ============================================================

-- ----------------------------
-- 1. 用户表
-- ----------------------------
CREATE TABLE `user` (
    `id`            BIGINT       NOT NULL COMMENT '雪花 ID',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname`      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `email`         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `avatar_url`    VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 禁用',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 2. OAuth2 客户端（Agent / 第三方凭证，Phase 3 使用）
-- ----------------------------
CREATE TABLE `oauth2_client` (
    `id`                BIGINT       NOT NULL COMMENT '雪花 ID',
    `client_id`         VARCHAR(64)  NOT NULL COMMENT '客户端 ID',
    `client_secret_hash` VARCHAR(100) DEFAULT NULL COMMENT '客户端密钥哈希（BCrypt）',
    `client_name`       VARCHAR(100) DEFAULT NULL COMMENT '客户端名称',
    `grant_types`       VARCHAR(255) DEFAULT NULL COMMENT '授权类型，逗号分隔',
    `scopes`            VARCHAR(255) DEFAULT NULL COMMENT '允许的 scope，逗号分隔',
    `redirect_uris`     VARCHAR(500) DEFAULT NULL COMMENT '回调地址，逗号分隔',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 禁用',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 客户端表';

-- ----------------------------
-- 3. 空间表
-- ----------------------------
CREATE TABLE `space` (
    `id`          BIGINT       NOT NULL COMMENT '雪花 ID',
    `name`        VARCHAR(100) NOT NULL COMMENT '空间名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '空间描述',
    `owner_id`    BIGINT       NOT NULL COMMENT '所有者用户 ID',
    `token_budget` BIGINT      DEFAULT NULL COMMENT '空间全局 Token 预算（Phase 3）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 禁用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_space_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间表';

-- ----------------------------
-- 4. 空间成员表
-- ----------------------------
CREATE TABLE `member` (
    `id`         BIGINT   NOT NULL COMMENT '雪花 ID',
    `space_id`   BIGINT   NOT NULL COMMENT '空间 ID',
    `user_id`    BIGINT   NOT NULL COMMENT '用户 ID',
    `role`       TINYINT  NOT NULL DEFAULT 3 COMMENT '角色：1 所有者 / 2 编辑者 / 3 观察者',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_space_user` (`space_id`, `user_id`),
    KEY `idx_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间成员表';

-- ----------------------------
-- 5. 文档表（树形目录 / 草稿正式双模式）
-- ----------------------------
CREATE TABLE `document` (
    `id`          BIGINT        NOT NULL COMMENT '雪花 ID',
    `space_id`    BIGINT        NOT NULL COMMENT '所属空间 ID',
    `parent_id`   BIGINT        NOT NULL DEFAULT 0 COMMENT '父目录 ID，0 为根',
    `title`       VARCHAR(200)  NOT NULL COMMENT '文档标题',
    `content`     LONGTEXT      DEFAULT NULL COMMENT 'Markdown 内容',
    `doc_type`    TINYINT       NOT NULL DEFAULT 1 COMMENT '类型：1 正式 / 2 草稿',
    `version`     BIGINT        NOT NULL DEFAULT 0 COMMENT '当前版本号',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 归档',
    `created_by`  BIGINT        DEFAULT NULL COMMENT '创建人用户 ID',
    `updated_by`  BIGINT        DEFAULT NULL COMMENT '更新人用户 ID',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_doc_space_parent` (`space_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- ----------------------------
-- 6. 文档版本表
-- ----------------------------
CREATE TABLE `document_version` (
    `id`            BIGINT   NOT NULL COMMENT '雪花 ID',
    `document_id`   BIGINT   NOT NULL COMMENT '文档 ID',
    `version_no`    BIGINT   NOT NULL COMMENT '版本号（从 1 开始递增）',
    `content`       LONGTEXT DEFAULT NULL COMMENT '该版本 Markdown 快照',
    `change_summary` VARCHAR(500) DEFAULT NULL COMMENT '变更摘要',
    `created_by`    BIGINT   DEFAULT NULL COMMENT '触发人用户 ID',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_version` (`document_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档版本表';

-- ----------------------------
-- 7. 变更请求表（审批流）
-- ----------------------------
CREATE TABLE `change_request` (
    `id`            BIGINT       NOT NULL COMMENT '雪花 ID',
    `document_id`   BIGINT       NOT NULL COMMENT '文档 ID',
    `request_type`  TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1 正式 / 2 草稿',
    `changes`       JSON         DEFAULT NULL COMMENT '结构化变更（JSON 数组）',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 待审批 / 1 已通过 / 2 已拒绝 / 3 已合并 / 4 已退回',
    `source_task_id` BIGINT      DEFAULT NULL COMMENT '来源任务 ID',
    `proposed_by`   BIGINT       DEFAULT NULL COMMENT '提交人（用户或 Agent ID）',
    `review_comment` VARCHAR(500) DEFAULT NULL COMMENT '审批批注',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_cr_document` (`document_id`),
    KEY `idx_cr_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='变更请求表';

-- ----------------------------
-- 8. Agent 表
-- ----------------------------
CREATE TABLE `agent` (
    `id`            BIGINT       NOT NULL COMMENT '雪花 ID',
    `space_id`      BIGINT       NOT NULL COMMENT '所属空间 ID',
    `name`          VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT 'Agent 描述',
    `client_id`     VARCHAR(64)  DEFAULT NULL COMMENT '关联 oauth2_client 的 client_id',
    `mcp_config`    TEXT         DEFAULT NULL COMMENT 'MCP 连接配置（Phase 3 加密存储）',
    `tool_whitelist` TEXT        DEFAULT NULL COMMENT '工具白名单（逗号分隔）',
    `doc_scope`     TEXT         DEFAULT NULL COMMENT '可读写文档范围（JSON）',
    `token_budget`  BIGINT       DEFAULT NULL COMMENT 'Agent Token 预算',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 0 禁用',
    `created_by`    BIGINT       DEFAULT NULL COMMENT '创建人用户 ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_agent_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 表';

-- ----------------------------
-- 9. 任务表
-- ----------------------------
CREATE TABLE `task` (
    `id`          BIGINT       NOT NULL COMMENT '雪花 ID',
    `space_id`    BIGINT       NOT NULL COMMENT '空间 ID',
    `agent_id`    BIGINT       DEFAULT NULL COMMENT 'Agent ID',
    `document_id` BIGINT       DEFAULT NULL COMMENT '目标文档 ID',
    `instruction` TEXT         DEFAULT NULL COMMENT '任务指令',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 待运行 / 1 运行中 / 2 已完成 / 3 已终止 / 4 异常',
    `token_budget` BIGINT      DEFAULT NULL COMMENT '任务级 Token 预算',
    `tokens_used` BIGINT       NOT NULL DEFAULT 0 COMMENT '已用 Token',
    `start_time`  DATETIME     DEFAULT NULL COMMENT '开始时间',
    `end_time`    DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_by`  BIGINT       DEFAULT NULL COMMENT '创建人用户 ID',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_task_space` (`space_id`),
    KEY `idx_task_agent` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- ----------------------------
-- 10. Token 用量表（四维度统计）
-- ----------------------------
CREATE TABLE `token_usage` (
    `id`          BIGINT   NOT NULL COMMENT '雪花 ID',
    `space_id`    BIGINT   DEFAULT NULL COMMENT '空间 ID',
    `task_id`     BIGINT   DEFAULT NULL COMMENT '任务 ID',
    `agent_id`    BIGINT   DEFAULT NULL COMMENT 'Agent ID',
    `document_id` BIGINT   DEFAULT NULL COMMENT '文档 ID',
    `dimension`   TINYINT  NOT NULL COMMENT '维度：1 空间 / 2 文档 / 3 任务 / 4 Agent',
    `tokens`      BIGINT   NOT NULL DEFAULT 0 COMMENT 'Token 数量',
    `usage_date`  DATE     NOT NULL COMMENT '统计日期',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tu_date` (`usage_date`),
    KEY `idx_tu_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token 用量表';

-- ----------------------------
-- 11. 审计日志表
-- ----------------------------
CREATE TABLE `audit_log` (
    `id`          BIGINT       NOT NULL COMMENT '雪花 ID',
    `space_id`    BIGINT       DEFAULT NULL COMMENT '空间 ID',
    `actor_type`  TINYINT      NOT NULL COMMENT '主体类型：1 人 / 2 Agent',
    `actor_id`    BIGINT       NOT NULL COMMENT '主体 ID',
    `action`      VARCHAR(50)  NOT NULL COMMENT '操作类型',
    `target_type` VARCHAR(50)  DEFAULT NULL COMMENT '目标类型',
    `target_id`   BIGINT       DEFAULT NULL COMMENT '目标 ID',
    `detail`      TEXT         DEFAULT NULL COMMENT '操作详情（JSON）',
    `ip`          VARCHAR(45)  DEFAULT NULL COMMENT '来源 IP',
    `trace_id`    VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪 ID',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_actor` (`actor_type`, `actor_id`),
    KEY `idx_audit_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';