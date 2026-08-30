-- ============================================================
-- Agent-Doc-Workbench 数据库初始化脚本 V1
-- 说明：v0.1 采用单一数据库 agent_doc_workbench，由 auth-service
--       统一托管 Flyway 迁移。所有表使用 InnoDB + utf8mb4 + 雪花 ID + 逻辑删除。
--       本文件已合并原 V1-V16，仅用于全新数据库初始化；后续变更从 V2 开始新增迁移。
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

-- ============================================================
-- 以下内容合并自 V2__model_and_token_stats.sql
-- ============================================================
-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V2
-- 说明：模型管理 + Token 用量统计细化
--  1. 新增 model 表（模型元数据表），agent 表新增 model_id 关联列与索引
--  2. token_usage 重构：task_id/agent_id/document_id 三列收敛为通用 obj_id，
--     新增唯一键 uk_dim_obj_date（删表重建，开发期无业务数据，不写数据搬迁）
--  3. 新增 token_usage_detail（Token 消耗原始调用明细【真相源】）
--     与 token_daily_snapshot（当日统计快照，用于今日消耗卡片）
-- MySQL 5.7 兼容：DATETIME，不依赖 MySQL 8 特性。
-- ============================================================

-- ----------------------------
-- 1. 模型元数据表 model
-- ----------------------------
CREATE TABLE `model` (
    `id`                       BIGINT       NOT NULL COMMENT '雪花 ID',
    `provider`                 VARCHAR(100) NOT NULL COMMENT '厂商名称：openai / dashscope / ollama / anthropic',
    `model_key`                VARCHAR(100) NOT NULL COMMENT '传给MCP服务的模型真实调用key',
    `display_name`             VARCHAR(100) NOT NULL COMMENT '前端展示友好名称',
    `official_url`             VARCHAR(255) DEFAULT NULL COMMENT '模型官网链接',
    `context_window`           BIGINT       DEFAULT NULL COMMENT '上下文窗口大小',
    `max_output_tokens`        BIGINT       DEFAULT NULL COMMENT '最大输出token',
    `input_price_per_million`  DECIMAL(12,6) DEFAULT 0.000000 COMMENT '输入单价，元/百万token，仅预估',
    `output_price_per_million` DECIMAL(12,6) DEFAULT 0.000000 COMMENT '输出单价，元/百万token，仅预估',
    `status`                   TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 /0禁用',
    `description`              TEXT         DEFAULT NULL COMMENT '备注说明',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_model_provider` (`provider`),
    KEY `idx_model_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型元数据表';

-- ----------------------------
-- 2. agent 表：新增 model_id 关联列与索引，更新 mcp_config 注释
-- ----------------------------
ALTER TABLE `agent`
    ADD COLUMN `model_id` BIGINT DEFAULT NULL COMMENT '关联模型id，关联model表' AFTER `client_id`,
    ADD KEY `idx_agent_model` (`model_id`),
    MODIFY COLUMN `mcp_config` TEXT DEFAULT NULL COMMENT 'MCP连接配置，应用层AES加密存储，禁止数据库明文存储密钥';

-- ----------------------------
-- 3. token_usage 重构：删表重建（开发期无业务数据）
--    四维列收敛为通用 obj_id + 唯一键，仅保留历史完整自然日，用于折线图
-- ----------------------------
DROP TABLE IF EXISTS `token_usage`;

CREATE TABLE `token_usage` (
    `id`          BIGINT   NOT NULL COMMENT '雪花 ID',
    `space_id`    BIGINT   DEFAULT NULL COMMENT '空间 ID',
    `dimension`   TINYINT  NOT NULL COMMENT '维度：1 空间 / 2 文档 / 3 任务 / 4 Agent',
    `obj_id`      BIGINT   NULL COMMENT '统计对象ID，配合dimension：1空间=space_id｜2文档=document_id｜3任务=task_id｜4Agent=agent_id',
    `tokens`      BIGINT   NOT NULL DEFAULT 0 COMMENT 'Token 当日增量数量',
    `usage_date`  DATE     NOT NULL COMMENT '统计日期',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dim_obj_date` (`dimension`, `obj_id`, `usage_date`),
    KEY `idx_tu_date` (`usage_date`),
    KEY `idx_tu_space` (`space_id`),
    KEY `idx_tu_obj` (`dimension`, `obj_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token用量聚合统计表(仅历史完整自然日，用于折线图)';

-- ----------------------------
-- 4. Token 消耗原始调用明细表【真相源，每一次MCP调用插入一条】
-- ----------------------------
CREATE TABLE `token_usage_detail` (
    `id`                  BIGINT        NOT NULL COMMENT '雪花 ID',
    `space_id`            BIGINT        NOT NULL COMMENT '所属空间ID',
    `task_id`             BIGINT        NOT NULL COMMENT '关联任务ID',
    `agent_id`            BIGINT        NOT NULL COMMENT '关联Agent ID',
    `model_id`            BIGINT        NOT NULL COMMENT '关联模型ID',
    `input_tokens`        BIGINT        NOT NULL DEFAULT 0 COMMENT '输入总token',
    `cached_input_tokens` BIGINT        DEFAULT NULL COMMENT '缓存命中输入token，MCP不支持则为NULL',
    `output_tokens`       BIGINT        NOT NULL DEFAULT 0 COMMENT '输出token',
    `call_time`           DATETIME      NOT NULL COMMENT 'MCP调用发生时间',
    `estimated_cost`      DECIMAL(14,6) DEFAULT 0.000000 COMMENT '预估人民币费用，仅展示，可重新核算',
    `trace_id`            VARCHAR(64)   DEFAULT NULL COMMENT '链路traceId，便于排查',
    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间',
    PRIMARY KEY (`id`),
    KEY `idx_tud_task` (`task_id`),
    KEY `idx_tud_agent` (`agent_id`),
    KEY `idx_tud_model` (`model_id`),
    KEY `idx_tud_call_time` (`call_time`),
    KEY `idx_tud_space` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token消耗原始调用明细表(权威数据源)';

-- ----------------------------
-- 5. token_daily_snapshot 当日统计快照表，用于页面今日消耗卡片
-- ----------------------------
CREATE TABLE `token_daily_snapshot` (
    `id`                   BIGINT        NOT NULL COMMENT '雪花ID',
    `space_id`             BIGINT        NOT NULL COMMENT '空间ID',
    `snapshot_date`        DATE          NOT NULL COMMENT '快照对应的业务日期',
    `total_input`          BIGINT        NOT NULL DEFAULT 0 COMMENT '快照时刻总输入token',
    `total_output`         BIGINT        NOT NULL DEFAULT 0 COMMENT '快照时刻总输出token',
    `total_estimated_cost` DECIMAL(14,6) DEFAULT 0.000000 COMMENT '快照时刻预估总费用',
    `snapshot_type`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1系统自动快照｜2用户手动触发快照',
    `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照生成时间',
    PRIMARY KEY (`id`),
    KEY `idx_snap_space_date` (`space_id`, `snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token当日统计快照，用于今日消耗卡片展示';

-- ============================================================
-- 以下内容合并自 V3__token_stats_indexes.sql
-- ============================================================
-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V3
-- 说明：Token 统计页面查询索引补充（设计依据 docs/database-design.md「查询索引」）
--  1. token_usage            折线图按"空间 + 日期范围"查询 → (space_id, usage_date)
--  2. token_daily_snapshot   今日卡片取"同空间同日最新快照" → (space_id, snapshot_date, created_at)
--     新索引完全覆盖 V2 的 idx_snap_space_date (space_id, snapshot_date)，故替换之，避免冗余索引
-- MySQL 5.7 兼容：5.7 不支持降序索引（DESC 会被忽略），最新快照查询走索引反向扫描，
--     故 DDL 中不写 DESC，由查询 ORDER BY created_at DESC 触发反向扫描。
-- ============================================================

-- ----------------------------
-- 1. token_usage：折线图按空间 + 日期范围过滤
-- ----------------------------
ALTER TABLE `token_usage`
    ADD KEY `idx_tu_space_date` (`space_id`, `usage_date`);

-- ----------------------------
-- 2. token_daily_snapshot：今日卡片取同空间同日最新快照（替换旧索引）
-- ----------------------------
ALTER TABLE `token_daily_snapshot`
    DROP KEY `idx_snap_space_date`,
    ADD KEY `idx_snap_space_date_created` (`space_id`, `snapshot_date`, `created_at`);

-- ============================================================
-- 以下内容合并自 V4__change_request_base_version.sql
-- ============================================================
-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V4
-- 说明：change_request 新增 base_version 列（审批合并时校验基线版本，防止并发覆盖）
-- ============================================================

ALTER TABLE `change_request`
    ADD COLUMN `base_version` BIGINT NOT NULL DEFAULT 0 COMMENT '目标文档基线版本号（合并时校验防并发覆盖）' AFTER `changes`;

-- ============================================================
-- 以下内容合并自 V5__document_parent_id_nullable.sql
-- ============================================================
-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V5
-- 说明：document.parent_id 允许 NULL（NULL 表示根目录，替代原 0 约定），存量 0 值迁移为 NULL
-- ============================================================

ALTER TABLE `document`
    MODIFY COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT '父目录 ID，NULL 为根' AFTER `space_id`;

UPDATE `document` SET `parent_id` = NULL WHERE `parent_id` = 0;

-- ============================================================
-- 以下内容合并自 V6__phase3_task_agent_audit.sql
-- ============================================================
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

-- ============================================================
-- 以下内容合并自 V7__agent_service_a2a_mcp.sql
-- ============================================================
-- Agent Service、A2A 任务映射与 MCP 远程执行所需结构。
ALTER TABLE `agent`
    ADD COLUMN `system_prompt` TEXT DEFAULT NULL COMMENT '用户可配置的 Agent 系统提示词' AFTER `description`,
    ADD COLUMN `config_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Agent 配置版本' AFTER `token_budget`,
    ADD COLUMN `max_iterations` INT NOT NULL DEFAULT 12 COMMENT '单次执行最大模型迭代次数' AFTER `config_version`,
    ADD COLUMN `execution_timeout_seconds` INT NOT NULL DEFAULT 600 COMMENT '单次执行超时秒数' AFTER `max_iterations`;

ALTER TABLE `model`
    ADD COLUMN `base_url` VARCHAR(500) DEFAULT NULL COMMENT 'OpenAI 兼容 API 地址' AFTER `official_url`,
    ADD COLUMN `encrypted_api_key` TEXT DEFAULT NULL COMMENT 'AES-GCM 加密后的模型 API Key' AFTER `base_url`;

CREATE TABLE `agent_execution` (
    `id`                     BIGINT       NOT NULL COMMENT '雪花 ID',
    `a2a_task_id`            VARCHAR(100) NOT NULL COMMENT 'A2A 标准任务 ID',
    `a2a_context_id`         VARCHAR(100) NOT NULL COMMENT 'A2A 标准上下文 ID',
    `workbench_task_id`      BIGINT       NOT NULL COMMENT 'Workbench Task ID，幂等键',
    `agent_id`               BIGINT       NOT NULL COMMENT 'Agent ID',
    `agent_config_version`   BIGINT       NOT NULL COMMENT '执行时 Agent 配置版本',
    `system_prompt_snapshot` LONGTEXT     NOT NULL COMMENT '执行时系统提示词快照',
    `model_snapshot`         TEXT         NOT NULL COMMENT '脱敏模型配置快照',
    `prompt_hash`            VARCHAR(64)  NOT NULL COMMENT '完整提示词 SHA-256',
    `status`                 VARCHAR(32)  NOT NULL COMMENT 'A2A 执行状态',
    `cancel_requested`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否请求取消',
    `input_tokens`           BIGINT       NOT NULL DEFAULT 0 COMMENT '输入 Token',
    `cached_input_tokens`    BIGINT       DEFAULT NULL COMMENT '缓存输入 Token',
    `output_tokens`          BIGINT       NOT NULL DEFAULT 0 COMMENT '输出 Token',
    `result_summary`         TEXT         DEFAULT NULL COMMENT '执行结果摘要',
    `error_message`          VARCHAR(2000) DEFAULT NULL COMMENT '失败原因',
    `started_at`             DATETIME     DEFAULT NULL COMMENT '开始时间',
    `finished_at`            DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_execution_a2a_task` (`a2a_task_id`),
    KEY `idx_agent_execution_context` (`a2a_context_id`),
    UNIQUE KEY `uk_agent_execution_workbench_task` (`workbench_task_id`),
    KEY `idx_agent_execution_agent_status` (`agent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent A2A 执行记录';

ALTER TABLE `task`
    ADD COLUMN `agent_config_version` BIGINT DEFAULT NULL COMMENT '执行时 Agent 配置版本' AFTER `agent_id`,
    ADD COLUMN `agent_execution_id` BIGINT DEFAULT NULL COMMENT 'Agent Service 执行 ID' AFTER `agent_config_version`,
    ADD COLUMN `a2a_task_id` VARCHAR(100) DEFAULT NULL COMMENT 'A2A 标准任务 ID' AFTER `agent_execution_id`,
    ADD COLUMN `a2a_context_id` VARCHAR(100) DEFAULT NULL COMMENT 'A2A 上下文 ID' AFTER `a2a_task_id`,
    ADD COLUMN `prompt_hash` VARCHAR(64) DEFAULT NULL COMMENT '执行提示词 SHA-256' AFTER `a2a_context_id`,
    ADD COLUMN `dispatched_at` DATETIME DEFAULT NULL COMMENT 'A2A 分发时间' AFTER `start_time`,
    ADD COLUMN `last_heartbeat_at` DATETIME DEFAULT NULL COMMENT 'A2A 最近状态时间' AFTER `dispatched_at`,
    ADD UNIQUE KEY `uk_task_a2a_task` (`a2a_task_id`),
    ADD KEY `idx_task_heartbeat` (`status`, `last_heartbeat_at`);

-- ============================================================
-- 以下内容合并自 V8__persist_a2a_protocol_state.sql
-- ============================================================
CREATE TABLE `a2a_task_store` (
    `task_id`           VARCHAR(100) NOT NULL COMMENT 'A2A Task ID',
    `context_id`        VARCHAR(100) DEFAULT NULL COMMENT 'A2A Context ID',
    `state`             VARCHAR(64)  DEFAULT NULL COMMENT 'A2A Task State',
    `status_timestamp`  DATETIME     DEFAULT NULL COMMENT '协议状态时间（UTC）',
    `encrypted_payload` LONGTEXT     NOT NULL COMMENT 'AES-GCM 加密的 A2A Task JSON',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`task_id`),
    KEY `idx_a2a_task_context` (`context_id`),
    KEY `idx_a2a_task_state_time` (`state`, `status_timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A 协议任务持久化';

CREATE TABLE `a2a_push_config` (
    `config_id`         VARCHAR(100) NOT NULL COMMENT 'Push Config ID',
    `task_id`           VARCHAR(100) NOT NULL COMMENT 'A2A Task ID',
    `protocol_version`  VARCHAR(32)  DEFAULT NULL COMMENT 'A2A 协议版本',
    `encrypted_payload` LONGTEXT     NOT NULL COMMENT 'AES-GCM 加密的 Push Config JSON',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`config_id`),
    KEY `idx_a2a_push_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A Push Notification 配置持久化';

-- ============================================================
-- 以下内容合并自 V9__multi_vendor_model_adapters.sql
-- ============================================================
-- 模型供应商与实际调用协议解耦，兼容历史 model 数据。
ALTER TABLE `model`
    ADD COLUMN `adapter_type` VARCHAR(64) DEFAULT NULL COMMENT '模型适配器类型：openai-chat / openai-compatible / anthropic-messages / google-genai' AFTER `provider`,
    ADD COLUMN `options_json` TEXT DEFAULT NULL COMMENT '适配器扩展配置 JSON' AFTER `encrypted_api_key`;

UPDATE `model`
SET `adapter_type` = CASE `provider`
    WHEN 'openai' THEN 'openai-chat'
    WHEN 'anthropic' THEN 'anthropic-messages'
    WHEN 'gemini' THEN 'google-genai'
    WHEN 'google-gemini' THEN 'google-genai'
    ELSE 'openai-compatible'
END
WHERE `adapter_type` IS NULL;

-- ============================================================
-- 以下内容合并自 V10__correct_model_column_comments.sql
-- ============================================================
-- 修正多厂商模型设计落地后 model 表字段注释与实际含义不一致的问题。
ALTER TABLE `model`
    MODIFY COLUMN `provider` VARCHAR(100) NOT NULL
        COMMENT '模型业务提供商编码：openai / anthropic / google-gemini / deepseek / zhipu-glm / alibaba-qwen / xiaomi-mimo / openai-compatible；兼容旧值 dashscope / ollama',
    MODIFY COLUMN `model_key` VARCHAR(100) NOT NULL
        COMMENT '模型调用标识，传递给模型适配器作为模型名称',
    MODIFY COLUMN `display_name` VARCHAR(100) NOT NULL
        COMMENT '模型前端展示名称',
    MODIFY COLUMN `official_url` VARCHAR(255) DEFAULT NULL
        COMMENT '模型官网或官方文档地址',
    MODIFY COLUMN `base_url` VARCHAR(500) DEFAULT NULL
        COMMENT '模型服务 API 基础地址，留空时使用供应商默认地址',
    MODIFY COLUMN `encrypted_api_key` TEXT DEFAULT NULL
        COMMENT 'AES-GCM 加密后的模型 API Key',
    MODIFY COLUMN `adapter_type` VARCHAR(64) DEFAULT NULL
        COMMENT '模型调用适配器类型：openai-chat / openai-compatible / anthropic-messages / google-genai',
    MODIFY COLUMN `options_json` TEXT DEFAULT NULL
        COMMENT '适配器扩展配置 JSON，按具体适配器使用',
    MODIFY COLUMN `context_window` BIGINT DEFAULT NULL
        COMMENT '模型上下文窗口大小，仅用于模型元数据',
    MODIFY COLUMN `max_output_tokens` BIGINT DEFAULT NULL
        COMMENT '模型允许的最大输出 Token 数',
    MODIFY COLUMN `input_price_per_million` DECIMAL(12,6) DEFAULT 0.000000
        COMMENT '输入单价，元/百万 Token，仅用于费用预估',
    MODIFY COLUMN `output_price_per_million` DECIMAL(12,6) DEFAULT 0.000000
        COMMENT '输出单价，元/百万 Token，仅用于费用预估',
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1
        COMMENT '模型状态：1 启用 / 0 禁用',
    MODIFY COLUMN `description` TEXT DEFAULT NULL
        COMMENT '模型备注说明';

-- ============================================================
-- 以下内容合并自 V11__preserve_token_usage_semantics.sql
-- ============================================================
-- 保留模型 Token 缺失语义，并记录本地估算来源；不回填历史0值，避免制造虚假的精确数据。
ALTER TABLE `agent_execution`
    MODIFY COLUMN `input_tokens` BIGINT DEFAULT NULL COMMENT '输入 Token，未获取时为 NULL',
    ADD COLUMN `input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输入 Token 是否为本地估算值' AFTER `input_tokens`,
    ADD COLUMN `cached_input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '缓存输入 Token 是否为本地估算值' AFTER `cached_input_tokens`,
    MODIFY COLUMN `output_tokens` BIGINT DEFAULT NULL COMMENT '输出 Token，未获取时为 NULL',
    ADD COLUMN `output_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输出 Token 是否为本地估算值' AFTER `output_tokens`;

ALTER TABLE `task`
    MODIFY COLUMN `tokens_used` BIGINT DEFAULT NULL COMMENT '已用 Token，无法获取时为 NULL',
    ADD COLUMN `tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '已用 Token 是否包含本地估算值' AFTER `tokens_used`;

ALTER TABLE `token_usage_detail`
    MODIFY COLUMN `input_tokens` BIGINT DEFAULT NULL COMMENT '输入 Token，未获取时为 NULL',
    ADD COLUMN `input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输入 Token 是否为本地估算值' AFTER `input_tokens`,
    ADD COLUMN `cached_input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '缓存输入 Token 是否为本地估算值' AFTER `cached_input_tokens`,
    MODIFY COLUMN `output_tokens` BIGINT DEFAULT NULL COMMENT '输出 Token，未获取时为 NULL',
    ADD COLUMN `output_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输出 Token 是否为本地估算值' AFTER `output_tokens`,
    MODIFY COLUMN `estimated_cost` DECIMAL(14,6) DEFAULT NULL COMMENT '预估人民币费用，Token缺失时为NULL，仅展示，可重新核算';

-- ============================================================
-- 以下内容合并自 V12__model_config_version.sql
-- ============================================================
-- ChatModel 缓存按模型配置版本隔离；历史模型从版本1开始。
ALTER TABLE `model`
    ADD COLUMN `config_version` BIGINT NOT NULL DEFAULT 1 COMMENT '模型配置版本，每次影响模型调用配置的修改递增' AFTER `options_json`;

-- ============================================================
-- 以下内容合并自 V13__skill_management.sql
-- ============================================================
CREATE TABLE `skill` (
    `id`              BIGINT       NOT NULL COMMENT '雪花 ID',
    `space_id`        BIGINT       NOT NULL COMMENT '所属空间 ID',
    `name`            VARCHAR(100) NOT NULL COMMENT 'Skill 名称，kebab-case',
    `description`     VARCHAR(500) NOT NULL COMMENT 'Skill 描述',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 ACTIVE / 0 DISABLED',
    `next_version_no` INT          NOT NULL DEFAULT 1 COMMENT '下一个待分配版本号',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人用户 ID',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_space_name_deleted` (`space_id`, `name`, `deleted`),
    KEY `idx_skill_space_status` (`space_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 元数据';

CREATE TABLE `skill_version` (
    `id`                      BIGINT       NOT NULL COMMENT '雪花 ID',
    `skill_id`                BIGINT       NOT NULL COMMENT 'Skill ID',
    `version_no`              INT          NOT NULL COMMENT '递增版本号',
    `status`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 DRAFT / 1 PUBLISHED',
    `storage_key`             VARCHAR(500) NOT NULL COMMENT '对象存储键',
    `sha256`                  VARCHAR(64)  NOT NULL COMMENT 'ZIP SHA-256',
    `package_size`            BIGINT       NOT NULL COMMENT 'ZIP 字节数',
    `uncompressed_size`       BIGINT       NOT NULL COMMENT '解压后累计字节数',
    `file_count`              INT          NOT NULL COMMENT '有效文件数量',
    `readable_resource_count` INT          NOT NULL DEFAULT 0 COMMENT '可被 Runtime 读取的资源数',
    `readable_resource_size`  BIGINT       NOT NULL DEFAULT 0 COMMENT '可读资源累计字节数',
    `instruction_text`        LONGTEXT     NOT NULL COMMENT '去除 Front Matter 后的 SKILL.md 正文',
    `manifest_json`           LONGTEXT     NOT NULL COMMENT '规范化文件清单 JSON',
    `allowed_tools_json`      TEXT         NOT NULL COMMENT 'Skill 声明工具名 JSON 数组',
    `created_by`              BIGINT       NOT NULL COMMENT '上传人用户 ID',
    `published_by`            BIGINT       DEFAULT NULL COMMENT '发布人用户 ID',
    `published_at`            DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_version_no` (`skill_id`, `version_no`),
    UNIQUE KEY `uk_skill_version_hash` (`skill_id`, `sha256`),
    KEY `idx_skill_version_status` (`skill_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 不可变版本';

CREATE TABLE `agent_skill` (
    `id`               BIGINT   NOT NULL COMMENT '雪花 ID',
    `agent_id`         BIGINT   NOT NULL COMMENT 'Agent ID',
    `skill_id`         BIGINT   NOT NULL COMMENT 'Skill ID',
    `skill_version_id` BIGINT   NOT NULL COMMENT '当前绑定的 Skill 版本 ID',
    `enabled`          TINYINT  NOT NULL DEFAULT 1 COMMENT '是否启用：1 是 / 0 否',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_skill` (`agent_id`, `skill_id`),
    KEY `idx_agent_skill_version` (`skill_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 当前 Skill 版本绑定';

ALTER TABLE `agent_execution`
    ADD COLUMN `skill_snapshot_json` LONGTEXT DEFAULT NULL COMMENT '执行时 Skill 版本快照 JSON' AFTER `model_snapshot`,
    ADD COLUMN `skill_instruction_hash` VARCHAR(64) DEFAULT NULL COMMENT 'Skill 指令集合 SHA-256' AFTER `skill_snapshot_json`,
    ADD COLUMN `tool_whitelist_snapshot` TEXT DEFAULT NULL COMMENT '执行时模型可见 MCP 工具名 JSON 数组' AFTER `skill_instruction_hash`;

-- V1 stored this field as comma-separated text. Normalize it once in V13;
-- NULL remains "no extra restriction" and blank text becomes an explicit empty list.
UPDATE `agent`
SET `tool_whitelist` = CASE
    WHEN TRIM(`tool_whitelist`) = '' THEN '[]'
    WHEN JSON_VALID(TRIM(`tool_whitelist`)) = 1
         AND JSON_TYPE(TRIM(`tool_whitelist`)) = 'ARRAY' THEN TRIM(`tool_whitelist`)
    ELSE CONCAT('[', REPLACE(JSON_QUOTE(TRIM(`tool_whitelist`)), ',', '","'), ']')
END
WHERE `tool_whitelist` IS NOT NULL;

ALTER TABLE `agent`
    MODIFY COLUMN `tool_whitelist` TEXT DEFAULT NULL COMMENT 'Agent MCP 工具白名单 JSON 数组；NULL 兼容为不额外限制';

-- ============================================================
-- 以下内容合并自 V14__skill_selection_metadata.sql
-- ============================================================
ALTER TABLE `skill`
    ADD COLUMN `display_name` VARCHAR(100) DEFAULT NULL COMMENT 'Skill 前端展示名称' AFTER `name`;

UPDATE `skill`
SET `display_name` = `name`
WHERE `display_name` IS NULL;

ALTER TABLE `skill`
    MODIFY COLUMN `display_name` VARCHAR(100) NOT NULL COMMENT 'Skill 前端展示名称';

ALTER TABLE `skill_version`
    ADD COLUMN `activation_description` VARCHAR(500) DEFAULT NULL
        COMMENT '版本级激活描述，来自 SKILL.md.description' AFTER `status`;

ALTER TABLE `agent`
    ADD COLUMN `skill_selection_mode` VARCHAR(32) NOT NULL
        COMMENT 'Skill 选择模式：ALL_BOUND / ROUTER' AFTER `model_id`,
    ADD COLUMN `skill_router_model_id` BIGINT DEFAULT NULL
        COMMENT 'Skill Router 模型 ID；为空时复用 Agent 主模型' AFTER `skill_selection_mode`;

ALTER TABLE `agent_execution`
    ADD COLUMN `user_instruction_snapshot` LONGTEXT DEFAULT NULL
        COMMENT '实际发送给主模型的初始用户指令快照' AFTER `system_prompt_snapshot`,
    ADD COLUMN `skill_selection_mode` VARCHAR(32) NOT NULL
        COMMENT 'Agent 配置的 Skill 选择模式：ALL_BOUND / ROUTER' AFTER `skill_instruction_hash`,
    ADD COLUMN `skill_selection_effective_mode` VARCHAR(32) NOT NULL
        COMMENT '实际执行模式：ALL_BOUND / ROUTER / ROUTER_FALLBACK' AFTER `skill_selection_mode`,
    ADD COLUMN `skill_router_model_id` BIGINT DEFAULT NULL
        COMMENT '本次实际使用的 Skill Router 模型 ID' AFTER `skill_selection_effective_mode`,
    ADD COLUMN `selected_skill_version_ids_json` TEXT DEFAULT NULL
        COMMENT '本次选中的 SkillVersion ID JSON 数组' AFTER `skill_router_model_id`,
    ADD COLUMN `skill_router_snapshot_json` LONGTEXT DEFAULT NULL
        COMMENT 'Skill Router 脱敏快照' AFTER `selected_skill_version_ids_json`,
    ADD COLUMN `tool_definition_snapshot_json` LONGTEXT DEFAULT NULL
        COMMENT '实际暴露给模型的工具定义快照' AFTER `tool_whitelist_snapshot`;

CREATE TABLE `agent_execution_tool_call` (
    `id`               BIGINT       NOT NULL COMMENT '雪花 ID',
    `execution_id`     BIGINT       NOT NULL COMMENT 'Agent 执行 ID',
    `sequence_no`      INT          NOT NULL COMMENT '本次执行内工具调用序号',
    `tool_name`        VARCHAR(128) NOT NULL COMMENT '工具名称',
    `tool_source`      VARCHAR(32)  NOT NULL COMMENT 'SKILL_LOCAL / MCP_REMOTE',
    `arguments_sha256` VARCHAR(64)  DEFAULT NULL COMMENT '参数 SHA-256',
    `arguments_size`   BIGINT       DEFAULT NULL COMMENT '参数 UTF-8 字节数',
    `result_sha256`    VARCHAR(64)  DEFAULT NULL COMMENT '结果 SHA-256',
    `result_size`      BIGINT       DEFAULT NULL COMMENT '结果 UTF-8 字节数',
    `status`           VARCHAR(16)  NOT NULL COMMENT 'STARTED / SUCCEEDED / FAILED',
    `error_type`       VARCHAR(128) DEFAULT NULL COMMENT '异常类型，不存异常正文',
    `started_at`       DATETIME     NOT NULL COMMENT '开始时间',
    `finished_at`      DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution_tool_sequence` (`execution_id`, `sequence_no`),
    KEY `idx_execution_tool_name` (`execution_id`, `tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 工具调用脱敏审计';

-- ============================================================
-- 以下内容合并自 V15__external_mcp_management.sql
-- ============================================================
ALTER TABLE `agent`
    ADD COLUMN `external_mcp_enabled` TINYINT(1) NOT NULL
        COMMENT '是否启用外部 MCP' AFTER `skill_router_model_id`;

CREATE TABLE `mcp_server` (
    `id`                   BIGINT       NOT NULL COMMENT '雪花 ID',
    `space_id`             BIGINT       NOT NULL COMMENT '所属空间 ID',
    `server_key`           VARCHAR(50)  NOT NULL COMMENT '稳定技术标识，kebab-case',
    `display_name`         VARCHAR(100) NOT NULL COMMENT '展示名称',
    `endpoint_url`         VARCHAR(500) NOT NULL COMMENT 'Streamable HTTP MCP 地址',
    `auth_type`            VARCHAR(16)  NOT NULL COMMENT 'NONE / BEARER',
    `encrypted_auth_token` TEXT         DEFAULT NULL COMMENT 'AES-GCM 加密认证令牌',
    `config_version`       BIGINT       NOT NULL DEFAULT 1 COMMENT '配置版本',
    `status`               TINYINT      NOT NULL DEFAULT 1 COMMENT '0 禁用 / 1 启用',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mcp_server_space_key` (`space_id`, `server_key`),
    KEY `idx_mcp_server_space_status` (`space_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间外部 MCP Server 配置';

CREATE TABLE `agent_mcp_binding` (
    `id`                  BIGINT      NOT NULL COMMENT '雪花 ID',
    `agent_id`            BIGINT      NOT NULL COMMENT 'Agent ID',
    `mcp_server_id`       BIGINT      NOT NULL COMMENT 'MCP Server ID',
    `tool_whitelist_json` TEXT        DEFAULT NULL COMMENT '远端原始工具名 JSON 数组；NULL 表示不额外限制',
    `enabled`             TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`             TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_mcp_server` (`agent_id`, `mcp_server_id`),
    KEY `idx_agent_mcp_enabled` (`agent_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 外部 MCP 绑定';

ALTER TABLE `agent_execution`
    ADD COLUMN `external_mcp_snapshot_json` LONGTEXT DEFAULT NULL
        COMMENT '外部 MCP 脱敏配置与绑定快照' AFTER `tool_definition_snapshot_json`;

ALTER TABLE `agent_execution_tool_call`
    ADD COLUMN `tool_source_key` VARCHAR(64) DEFAULT NULL
        COMMENT 'workbench / skill-local / 外部 MCP server_key' AFTER `tool_source`,
    ADD COLUMN `mcp_server_id` BIGINT DEFAULT NULL
        COMMENT '外部 MCP Server ID，内置和本地工具为空' AFTER `tool_source_key`;

-- ============================================================
-- 以下内容合并自 V16__execution_model_call_audit.sql
-- ============================================================
ALTER TABLE `skill_version`
    MODIFY COLUMN `activation_description` VARCHAR(500) NOT NULL
        COMMENT '版本级激活描述，来自 SKILL.md.description';

CREATE TABLE `agent_execution_model_call` (
    `id`                   BIGINT       NOT NULL COMMENT '雪花 ID',
    `execution_id`         BIGINT       NOT NULL COMMENT 'Agent 执行 ID',
    `sequence_no`          INT          NOT NULL COMMENT '本次执行内模型调用序号',
    `model_id`             BIGINT       NOT NULL COMMENT '实际模型 ID',
    `model_config_version` BIGINT       NOT NULL COMMENT '实际模型配置版本',
    `model_key`            VARCHAR(100) NOT NULL COMMENT '实际模型标识',
    `max_output_tokens`    INT          DEFAULT NULL COMMENT '本轮最大输出 Token',
    `temperature`          DOUBLE       DEFAULT NULL COMMENT '本轮温度参数',
    `streaming`            TINYINT(1)   NOT NULL COMMENT '是否流式调用',
    `messages_sha256`      VARCHAR(64)  NOT NULL COMMENT '实际消息序列 SHA-256',
    `messages_size`        BIGINT       NOT NULL COMMENT '规范化消息 UTF-8 字节数',
    `response_sha256`      VARCHAR(64)  DEFAULT NULL COMMENT '规范化响应 SHA-256',
    `response_size`        BIGINT       DEFAULT NULL COMMENT '规范化响应 UTF-8 字节数',
    `status`               VARCHAR(16)  NOT NULL COMMENT 'STARTED / SUCCEEDED / FAILED',
    `error_type`           VARCHAR(128) DEFAULT NULL COMMENT '异常类型，不存异常正文',
    `started_at`           DATETIME     NOT NULL COMMENT '开始时间',
    `finished_at`          DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution_model_sequence` (`execution_id`, `sequence_no`),
    KEY `idx_execution_model_status` (`execution_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 单轮模型调用脱敏审计';
