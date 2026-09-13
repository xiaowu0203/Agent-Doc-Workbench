-- ============================================================
-- Phase 7：MCP 模板拆分不可变版本
-- V19 已执行环境通过本迁移向前升级，不修改 V19 历史校验和。
-- ============================================================

CREATE TABLE `mcp_template_version` (
    `id`              BIGINT       NOT NULL COMMENT '雪花 ID',
    `template_id`     BIGINT       NOT NULL COMMENT 'MCP 模板 ID',
    `version_no`      INT          NOT NULL COMMENT '递增版本号',
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 DRAFT / 1 PUBLISHED',
    `display_name`    VARCHAR(100) NOT NULL COMMENT '安装后的默认展示名称',
    `endpoint_url`    VARCHAR(500) NOT NULL COMMENT '默认公网 HTTPS Endpoint',
    `auth_type`       VARCHAR(16)  NOT NULL COMMENT 'NONE / BEARER / QUERY_PARAM',
    `auth_param_name` VARCHAR(100) DEFAULT NULL COMMENT 'Query API Key 参数名',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人用户 ID',
    `published_by`    BIGINT       DEFAULT NULL COMMENT '发布人用户 ID',
    `published_at`    DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mcp_template_version_no` (`template_id`, `version_no`),
    KEY `idx_mcp_template_version_status` (`template_id`, `status`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 模板不可变版本';

-- V19 的每个模板当前配置回填为一个已发布版本。版本 ID 复用模板 ID；
-- 两者位于不同表，不存在主键冲突，后续新版本仍由雪花算法分配 ID。
INSERT INTO `mcp_template_version` (
    `id`, `template_id`, `version_no`, `status`, `display_name`, `endpoint_url`,
    `auth_type`, `auth_param_name`, `created_by`, `published_by`, `published_at`, `created_at`
)
SELECT
    `id`, `id`, `config_version`, 1, `display_name`, `endpoint_url`,
    `auth_type`, `auth_param_name`, `created_by`, `created_by`, `updated_at`, `created_at`
FROM `mcp_template`
WHERE `deleted` = 0;

-- 如果 V19 期间模板更新过，优先从已安装的空间实例恢复仍被引用的历史配置。
-- 同一模板版本存在多个空间实例时取 ID 最小的一条作为配置来源；凭证不进入模板版本。
INSERT INTO `mcp_template_version` (
    `id`, `template_id`, `version_no`, `status`, `display_name`, `endpoint_url`,
    `auth_type`, `auth_param_name`, `created_by`, `published_by`, `published_at`, `created_at`
)
SELECT
    ms.`id`, ms.`template_id`, ms.`template_version`, 1, ms.`display_name`, ms.`endpoint_url`,
    ms.`auth_type`, ms.`auth_param_name`, mt.`created_by`, mt.`created_by`, ms.`created_at`, ms.`created_at`
FROM `mcp_server` ms
JOIN (
    SELECT `template_id`, `template_version`, MIN(`id`) AS `source_server_id`
    FROM `mcp_server`
    WHERE `template_id` IS NOT NULL AND `template_version` IS NOT NULL AND `deleted` = 0
    GROUP BY `template_id`, `template_version`
) selected_server
  ON selected_server.`source_server_id` = ms.`id`
JOIN `mcp_template` mt ON mt.`id` = ms.`template_id`
LEFT JOIN `mcp_template_version` existing
  ON existing.`template_id` = ms.`template_id`
 AND existing.`version_no` = ms.`template_version`
WHERE existing.`id` IS NULL;

ALTER TABLE `mcp_template`
    ADD COLUMN `next_version_no` INT NOT NULL DEFAULT 1 COMMENT '下一个待分配版本号' AFTER `status`;

UPDATE `mcp_template`
SET `next_version_no` = `config_version` + 1;

ALTER TABLE `agent_template_mcp`
    ADD COLUMN `mcp_template_version_id` BIGINT DEFAULT NULL
        COMMENT '固定的 MCP 模板版本 ID' AFTER `mcp_template_version`;

UPDATE `agent_template_mcp` atm
JOIN `mcp_template_version` mtv
  ON mtv.`template_id` = atm.`mcp_template_id`
 AND mtv.`version_no` = atm.`mcp_template_version`
SET atm.`mcp_template_version_id` = mtv.`id`;

ALTER TABLE `agent_template_mcp`
    MODIFY COLUMN `mcp_template_version_id` BIGINT NOT NULL COMMENT '固定的 MCP 模板版本 ID',
    DROP COLUMN `mcp_template_version`,
    ADD KEY `idx_agent_template_mcp_version` (`mcp_template_version_id`);

ALTER TABLE `mcp_server`
    ADD COLUMN `template_version_id` BIGINT DEFAULT NULL
        COMMENT '安装时采用的 MCP 模板版本 ID' AFTER `template_version`;

UPDATE `mcp_server` ms
JOIN `mcp_template_version` mtv
  ON mtv.`template_id` = ms.`template_id`
 AND mtv.`version_no` = ms.`template_version`
SET ms.`template_version_id` = mtv.`id`
WHERE ms.`template_id` IS NOT NULL;

ALTER TABLE `mcp_server`
    DROP INDEX `idx_mcp_server_template_source`,
    DROP COLUMN `template_version`,
    ADD KEY `idx_mcp_server_template_source` (`template_id`, `template_version_id`);

ALTER TABLE `mcp_template`
    DROP COLUMN `endpoint_url`,
    DROP COLUMN `auth_type`,
    DROP COLUMN `auth_param_name`,
    DROP COLUMN `config_version`;
