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
