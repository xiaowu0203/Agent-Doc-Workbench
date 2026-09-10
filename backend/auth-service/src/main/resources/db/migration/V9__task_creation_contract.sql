-- Phase 6：任务列表/创建页契约、任务草稿、可读编号和文档读取范围。

ALTER TABLE `task`
    ADD COLUMN `task_no` VARCHAR(64) DEFAULT NULL COMMENT '可读任务编号' AFTER `id`,
    ADD COLUMN `document_type` TINYINT DEFAULT NULL COMMENT '创建任务时的文档类型快照：1 正式 / 2 草稿' AFTER `document_id`,
    ADD COLUMN `read_scope` VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT '文档读取范围：FULL / RANGE' AFTER `token_budget`,
    ADD COLUMN `read_start` BIGINT DEFAULT NULL COMMENT '允许读取的起始字符偏移' AFTER `read_scope`,
    ADD COLUMN `read_end` BIGINT DEFAULT NULL COMMENT '允许读取的结束字符偏移（不含）' AFTER `read_start`;

UPDATE `task` t
LEFT JOIN `document` d ON d.`id` = t.`document_id`
SET t.`task_no` = CONCAT('T-LEGACY-', t.`id`),
    t.`document_type` = d.`doc_type`
WHERE t.`task_no` IS NULL;

ALTER TABLE `task`
    MODIFY COLUMN `task_no` VARCHAR(64) NOT NULL COMMENT '可读任务编号',
    ADD UNIQUE KEY `uk_task_no` (`task_no`);

CREATE TABLE `task_draft` (
    `id`            BIGINT       NOT NULL COMMENT '雪花 ID',
    `space_id`      BIGINT       NOT NULL COMMENT '所属空间 ID',
    `agent_id`      BIGINT       DEFAULT NULL COMMENT 'Agent ID',
    `document_id`   BIGINT       DEFAULT NULL COMMENT '目标文档 ID',
    `name`          VARCHAR(100) DEFAULT NULL COMMENT '任务名称',
    `instruction`   TEXT         DEFAULT NULL COMMENT '任务指令',
    `token_budget`  BIGINT       DEFAULT NULL COMMENT '任务 Token 预算',
    `read_scope`    VARCHAR(16)  NOT NULL DEFAULT 'FULL' COMMENT '文档读取范围：FULL / RANGE',
    `read_start`    BIGINT       DEFAULT NULL COMMENT '读取起始字符偏移',
    `read_length`   BIGINT       DEFAULT NULL COMMENT '读取字符数量',
    `created_by`    BIGINT       NOT NULL COMMENT '草稿所有者用户 ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_task_draft_owner_space` (`created_by`, `space_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户任务表单草稿';
