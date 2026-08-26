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
