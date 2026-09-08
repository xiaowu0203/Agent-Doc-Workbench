ALTER TABLE `change_request`
    ADD COLUMN `space_id` BIGINT DEFAULT NULL COMMENT '所属空间 ID 快照' AFTER `id`,
    ADD COLUMN `summary` VARCHAR(500) DEFAULT NULL COMMENT '变更摘要' AFTER `changes`,
    ADD COLUMN `assigned_reviewer_id` BIGINT DEFAULT NULL COMMENT '当前认领审批人用户 ID' AFTER `proposed_actor_type`,
    ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL COMMENT '最终审批人用户 ID' AFTER `assigned_reviewer_id`,
    ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL COMMENT '最终审批时间' AFTER `reviewed_by`,
    ADD COLUMN `resolution_type` VARCHAR(20) DEFAULT NULL COMMENT '接受方式：ALL / PARTIAL / EDITED' AFTER `review_comment`,
    ADD COLUMN `accepted_change_keys` JSON DEFAULT NULL COMMENT '部分接受的 Diff 块稳定标识' AFTER `resolution_type`,
    ADD COLUMN `resolved_content` LONGTEXT DEFAULT NULL COMMENT '部分接受或修改后接受的最终 Markdown' AFTER `accepted_change_keys`,
    ADD COLUMN `merged_by` BIGINT DEFAULT NULL COMMENT '合并人用户 ID' AFTER `resolved_content`,
    ADD COLUMN `merged_at` DATETIME DEFAULT NULL COMMENT '合并时间' AFTER `merged_by`,
    ADD COLUMN `merged_version` BIGINT DEFAULT NULL COMMENT '合并后文档版本号' AFTER `merged_at`,
    ADD COLUMN `parent_request_id` BIGINT DEFAULT NULL COMMENT '退回重改前一条变更请求 ID' AFTER `merged_version`,
    ADD COLUMN `revision_no` INT NOT NULL DEFAULT 1 COMMENT '同一重改链中的修订序号' AFTER `parent_request_id`,
    ADD COLUMN `rework_task_id` BIGINT DEFAULT NULL COMMENT '退回后创建的重改任务 ID' AFTER `revision_no`;

UPDATE `change_request` cr
JOIN `document` d ON d.`id` = cr.`document_id`
SET cr.`space_id` = d.`space_id`
WHERE cr.`space_id` IS NULL;

ALTER TABLE `change_request`
    MODIFY COLUMN `space_id` BIGINT NOT NULL COMMENT '所属空间 ID 快照',
    ADD KEY `idx_cr_space_status_created` (`space_id`, `status`, `created_at`),
    ADD KEY `idx_cr_assignee_status` (`assigned_reviewer_id`, `status`),
    ADD KEY `idx_cr_source_task` (`source_task_id`),
    ADD KEY `idx_cr_parent` (`parent_request_id`),
    ADD UNIQUE KEY `uk_cr_rework_task` (`rework_task_id`);

ALTER TABLE `document_version`
    ADD COLUMN `source_change_request_id` BIGINT DEFAULT NULL COMMENT '审批合并来源变更请求 ID' AFTER `created_by`,
    ADD UNIQUE KEY `uk_doc_version_change_request` (`source_change_request_id`);

-- 历史上新建文档不会保存 v0 快照；补齐当前版本可保证新审批请求总能读取基线。
INSERT INTO `document_version`
    (`id`, `document_id`, `version_no`, `content`, `change_summary`, `created_by`, `created_at`, `deleted`)
SELECT 0 - d.`id`, d.`id`, d.`version`, d.`content`, '迁移补齐当前版本基线', d.`updated_by`,
       COALESCE(d.`updated_at`, d.`created_at`), 0
FROM `document` d
WHERE NOT EXISTS (
    SELECT 1
    FROM `document_version` dv
    WHERE dv.`document_id` = d.`id`
      AND dv.`version_no` = d.`version`
);

CREATE TABLE `change_request_comment` (
    `id`                BIGINT       NOT NULL COMMENT '雪花 ID',
    `change_request_id` BIGINT       NOT NULL COMMENT '变更请求 ID',
    `change_key`        VARCHAR(100) DEFAULT NULL COMMENT '关联 Diff 块标识；空表示整单批注',
    `author_id`         BIGINT       NOT NULL COMMENT '批注人用户 ID',
    `content`           VARCHAR(500) NOT NULL COMMENT '批注内容',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_cr_comment_request_created` (`change_request_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='变更请求追加型批注';
