/*
 * 修复 V14 之前 Agent 草稿版本使用旧摘要且未保存来源任务的问题。
 * 仅处理能通过文档、任务执行时间窗口唯一匹配的版本，无法唯一判断的记录保留 UNKNOWN，避免误关联。
 */
UPDATE `document_version` dv
JOIN `task` t
  ON t.`document_id` = dv.`document_id`
 AND t.`deleted` = 0
 AND dv.`created_at` >= COALESCE(t.`start_time`, t.`created_at`)
 AND (t.`end_time` IS NULL OR dv.`created_at` <= t.`end_time`)
LEFT JOIN `task` t2
  ON t2.`document_id` = dv.`document_id`
 AND t2.`deleted` = 0
 AND t2.`id` <> t.`id`
 AND dv.`created_at` >= COALESCE(t2.`start_time`, t2.`created_at`)
 AND (t2.`end_time` IS NULL OR dv.`created_at` <= t2.`end_time`)
SET dv.`source_type` = 'AGENT_DRAFT',
    dv.`actor_type` = 'AGENT',
    dv.`actor_id` = t.`agent_id`,
    dv.`source_task_id` = t.`id`
WHERE dv.`source_type` = 'UNKNOWN'
  AND dv.`change_summary` IN ('Agent 任务直接更新草稿', 'Agent 更新草稿')
  AND t.`agent_id` IS NOT NULL
  AND t2.`id` IS NULL;
