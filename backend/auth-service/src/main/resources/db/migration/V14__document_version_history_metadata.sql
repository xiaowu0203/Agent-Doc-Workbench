ALTER TABLE `document_version`
    ADD COLUMN `source_type` VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT '版本来源：UNKNOWN / CREATE / HUMAN_EDIT / AGENT_DRAFT / APPROVAL_MERGE / ROLLBACK'
        AFTER `change_summary`,
    ADD COLUMN `actor_type` VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT '版本操作主体：UNKNOWN / HUMAN / AGENT' AFTER `source_type`,
    ADD COLUMN `actor_id` BIGINT DEFAULT NULL COMMENT '版本操作主体 ID' AFTER `actor_type`,
    ADD COLUMN `source_task_id` BIGINT DEFAULT NULL COMMENT '来源任务 ID' AFTER `source_change_request_id`,
    ADD COLUMN `rollback_from_version` BIGINT DEFAULT NULL COMMENT '回滚来源版本号' AFTER `source_task_id`,
    ADD COLUMN `content_sha256` VARCHAR(64) DEFAULT NULL COMMENT '正文快照 SHA-256' AFTER `rollback_from_version`,
    ADD KEY `idx_doc_version_source_task` (`source_task_id`);

UPDATE `document_version` dv
LEFT JOIN `change_request` cr ON cr.`id` = dv.`source_change_request_id`
SET dv.`source_type` = CASE
        WHEN dv.`source_change_request_id` IS NOT NULL THEN 'APPROVAL_MERGE'
        WHEN dv.`change_summary` = '创建文档' THEN 'CREATE'
        WHEN dv.`change_summary` = '编辑更新内容' THEN 'HUMAN_EDIT'
        WHEN dv.`change_summary` = 'Agent 更新草稿' THEN 'AGENT_DRAFT'
        WHEN dv.`change_summary` LIKE '回滚至版本 %' THEN 'ROLLBACK'
        ELSE 'UNKNOWN'
    END,
    dv.`actor_type` = CASE
        WHEN dv.`change_summary` = 'Agent 更新草稿' THEN 'AGENT'
        WHEN dv.`source_change_request_id` IS NOT NULL
            OR dv.`change_summary` IN ('创建文档', '编辑更新内容')
            OR dv.`change_summary` LIKE '回滚至版本 %' THEN 'HUMAN'
        ELSE 'UNKNOWN'
    END,
    dv.`actor_id` = CASE
        WHEN dv.`source_change_request_id` IS NOT NULL
            OR dv.`change_summary` IN ('创建文档', '编辑更新内容', 'Agent 更新草稿')
            OR dv.`change_summary` LIKE '回滚至版本 %' THEN dv.`created_by`
        ELSE NULL
    END,
    dv.`source_task_id` = cr.`source_task_id`,
    dv.`rollback_from_version` = CASE
        WHEN dv.`change_summary` LIKE '回滚至版本 %'
            THEN CAST(SUBSTRING(dv.`change_summary`, CHAR_LENGTH('回滚至版本 ') + 1) AS UNSIGNED)
        ELSE NULL
    END,
    dv.`content_sha256` = SHA2(COALESCE(dv.`content`, ''), 256);

ALTER TABLE `document_version`
    MODIFY COLUMN `content_sha256` VARCHAR(64) NOT NULL COMMENT '正文快照 SHA-256';
