-- ============================================================
-- Phase 7：系统能力目录与空间安装
-- 系统定义只提供可复用模板；实际执行仍以空间安装、空间实例和执行快照为边界。
-- MySQL 5.7 兼容；V1～V18 不再修改。
-- ============================================================

-- Skill 增加 SYSTEM / SPACE 作用域。scope_owner_id 用于让 SYSTEM 的 NULL space_id
-- 也能参与唯一约束，避免 MySQL 允许多个 NULL 导致系统 Skill 重名。
ALTER TABLE `skill`
    DROP INDEX `uk_skill_space_name_deleted`,
    DROP INDEX `idx_skill_space_status`,
    MODIFY COLUMN `space_id` BIGINT DEFAULT NULL COMMENT '所属空间 ID；系统 Skill 为空',
    ADD COLUMN `scope_type` VARCHAR(16) NOT NULL DEFAULT 'SPACE'
        COMMENT '作用域：SYSTEM / SPACE' AFTER `id`,
    ADD COLUMN `scope_owner_id` BIGINT
        GENERATED ALWAYS AS (IFNULL(`space_id`, 0)) STORED
        COMMENT '作用域唯一键辅助列；系统作用域固定为 0' AFTER `space_id`,
    ADD UNIQUE KEY `uk_skill_scope_name_deleted`
        (`scope_type`, `scope_owner_id`, `name`, `deleted`),
    ADD KEY `idx_skill_scope_status` (`scope_type`, `status`, `updated_at`),
    ADD KEY `idx_skill_space_status` (`space_id`, `status`, `updated_at`);

UPDATE `skill`
SET `scope_type` = 'SPACE'
WHERE `scope_type` <> 'SPACE' OR `scope_type` IS NULL;

CREATE TABLE `space_skill_installation` (
    `id`               BIGINT      NOT NULL COMMENT '雪花 ID',
    `space_id`         BIGINT      NOT NULL COMMENT '安装目标空间 ID',
    `skill_id`         BIGINT      NOT NULL COMMENT '系统 Skill ID',
    `skill_version_id` BIGINT      NOT NULL COMMENT '空间固定使用的已发布版本 ID',
    `enabled`          TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否在当前空间启用',
    `installed_by`     BIGINT      NOT NULL COMMENT '安装人用户 ID',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_skill_installation` (`space_id`, `skill_id`),
    KEY `idx_space_skill_enabled` (`space_id`, `enabled`, `updated_at`),
    KEY `idx_space_skill_version` (`skill_version_id`),
    KEY `idx_system_skill_installations` (`skill_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='空间安装的系统 Skill 及固定版本';

-- Agent 系统模板及不可变版本。模板不进入任务执行链，只能安装为空间 Agent。
CREATE TABLE `agent_template` (
    `id`              BIGINT       NOT NULL COMMENT '雪花 ID',
    `name`            VARCHAR(100) NOT NULL COMMENT '稳定技术名称',
    `display_name`    VARCHAR(100) NOT NULL COMMENT '展示名称',
    `description`     VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 ACTIVE / 0 DISABLED',
    `next_version_no` INT          NOT NULL DEFAULT 1 COMMENT '下一个待分配版本号',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人用户 ID',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_template_name_deleted` (`name`, `deleted`),
    KEY `idx_agent_template_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统 Agent 模板';

CREATE TABLE `agent_template_version` (
    `id`                        BIGINT       NOT NULL COMMENT '雪花 ID',
    `template_id`               BIGINT       NOT NULL COMMENT 'Agent 模板 ID',
    `version_no`                INT          NOT NULL COMMENT '递增版本号',
    `status`                    TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 DRAFT / 1 PUBLISHED',
    `display_name`              VARCHAR(100) NOT NULL COMMENT '该版本默认展示名称',
    `description`               VARCHAR(500) DEFAULT NULL COMMENT '该版本说明',
    `system_prompt`             TEXT         DEFAULT NULL COMMENT '默认系统提示词',
    `model_id`                  BIGINT       DEFAULT NULL COMMENT '默认模型 ID',
    `skill_selection_mode`      VARCHAR(32)  NOT NULL COMMENT '默认 Skill 选择模式：ALL_BOUND / ROUTER',
    `skill_router_model_id`     BIGINT       DEFAULT NULL COMMENT '默认 Skill Router 模型 ID',
    `external_mcp_enabled`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '默认是否启用外部 MCP',
    `token_budget`              BIGINT       DEFAULT NULL COMMENT '默认 Token 预算',
    `tool_whitelist`            TEXT         DEFAULT NULL COMMENT '默认工具白名单 JSON 数组',
    `max_iterations`            INT          NOT NULL DEFAULT 12 COMMENT '默认最大模型迭代次数',
    `execution_timeout_seconds` INT          NOT NULL DEFAULT 600 COMMENT '默认执行超时秒数',
    `created_by`                BIGINT       NOT NULL COMMENT '创建人用户 ID',
    `published_by`              BIGINT       DEFAULT NULL COMMENT '发布人用户 ID',
    `published_at`              DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_template_version_no` (`template_id`, `version_no`),
    KEY `idx_agent_template_version_status` (`template_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 模板不可变版本';

CREATE TABLE `agent_template_skill` (
    `id`                  BIGINT   NOT NULL COMMENT '雪花 ID',
    `template_version_id` BIGINT   NOT NULL COMMENT 'Agent 模板版本 ID',
    `skill_id`            BIGINT   NOT NULL COMMENT '系统 Skill ID',
    `skill_version_id`    BIGINT   NOT NULL COMMENT '固定的系统 Skill 版本 ID',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_template_skill` (`template_version_id`, `skill_id`),
    KEY `idx_agent_template_skill_version` (`skill_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 模板版本的系统 Skill 引用';

-- MCP 模板只保存公开连接元数据，不保存任何空间凭证。
CREATE TABLE `mcp_template` (
    `id`                    BIGINT       NOT NULL COMMENT '雪花 ID',
    `server_key`            VARCHAR(50)  NOT NULL COMMENT '稳定技术标识，kebab-case',
    `display_name`          VARCHAR(100) NOT NULL COMMENT '展示名称',
    `description`           VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
    `endpoint_url`          VARCHAR(500) NOT NULL COMMENT '默认公网 HTTPS Endpoint',
    `auth_type`             VARCHAR(16)  NOT NULL COMMENT 'NONE / BEARER / QUERY_PARAM',
    `auth_param_name`       VARCHAR(100) DEFAULT NULL COMMENT 'Query API Key 参数名',
    `config_version`        BIGINT       NOT NULL DEFAULT 1 COMMENT '模板配置版本',
    `status`                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 ACTIVE / 0 DISABLED',
    `discovered_tool_count` INT          DEFAULT NULL COMMENT '平台最近一次发现的工具数量',
    `discovered_tools_json` LONGTEXT     DEFAULT NULL COMMENT '平台最近一次工具定义快照',
    `tools_discovered_at`   DATETIME     DEFAULT NULL COMMENT '平台工具发现时间',
    `created_by`            BIGINT       NOT NULL COMMENT '创建人用户 ID',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mcp_template_key_deleted` (`server_key`, `deleted`),
    KEY `idx_mcp_template_status` (`status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统 MCP 模板';

CREATE TABLE `agent_template_mcp` (
    `id`                   BIGINT   NOT NULL COMMENT '雪花 ID',
    `template_version_id`  BIGINT   NOT NULL COMMENT 'Agent 模板版本 ID',
    `mcp_template_id`      BIGINT   NOT NULL COMMENT '系统 MCP 模板 ID',
    `mcp_template_version` BIGINT   NOT NULL COMMENT '引用时的 MCP 模板配置版本',
    `tool_whitelist_json`  TEXT     DEFAULT NULL COMMENT '默认远端工具白名单 JSON 数组',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_template_mcp` (`template_version_id`, `mcp_template_id`),
    KEY `idx_agent_template_mcp_template` (`mcp_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 模板版本的 MCP 模板引用';

ALTER TABLE `agent`
    ADD COLUMN `template_id` BIGINT DEFAULT NULL COMMENT '来源系统 Agent 模板 ID' AFTER `id`,
    ADD COLUMN `template_version_id` BIGINT DEFAULT NULL COMMENT '安装时采用的模板版本 ID' AFTER `template_id`,
    ADD KEY `idx_agent_template_source` (`template_id`, `template_version_id`);

ALTER TABLE `mcp_server`
    ADD COLUMN `template_id` BIGINT DEFAULT NULL COMMENT '来源系统 MCP 模板 ID' AFTER `id`,
    ADD COLUMN `template_version` BIGINT DEFAULT NULL COMMENT '安装时采用的模板配置版本' AFTER `template_id`,
    ADD KEY `idx_mcp_server_template_source` (`template_id`, `template_version`);
