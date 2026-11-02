/*
 * V23 迁移前、独立于 Flyway 执行的只读完整性检查（MySQL 5.7）。
 *
 * 本脚本不修改业务表。最后的循环检查只写会话级 TEMPORARY TABLE；连接断开即释放。
 * 本文件不是数据库迁移，禁止放入 Flyway db/migration 目录。
 * 一对一、重改关系/审计与父链异常结果需在升级前人工处置；缺失来源的历史 Token、
 * 缺失历史价格快照及没有 TASK_RETRY 审计的派生 Task 是信息性结果，记录并人工核查。
 * 必须包含 deleted=1 的 change_request，因为历史血缘证据不能被逻辑删除过滤。
 * 请在同一交互式会话按段执行：第 5 节 UPDATE 必须重复到无未完成行后再查询和删除临时表；
 * 不要将整个文件作为一次性非交互脚本执行，否则无法完成多层父链检查。
 */

-- 1. Task 与 AgentExecution 一对一、双向引用一致性
SELECT `workbench_task_id`, COUNT(*) AS `anomaly_count`
FROM `agent_execution`
GROUP BY `workbench_task_id`
HAVING COUNT(*) <> 1;

SELECT ae.`id` AS `execution_id`, ae.`workbench_task_id`
FROM `agent_execution` ae
LEFT JOIN `task` t ON t.`id` = ae.`workbench_task_id`
WHERE t.`id` IS NULL;

SELECT t.`id` AS `task_id`, t.`agent_execution_id`, ae.`workbench_task_id`
FROM `task` t
LEFT JOIN `agent_execution` ae ON ae.`id` = t.`agent_execution_id`
WHERE t.`agent_execution_id` IS NOT NULL
  AND (ae.`id` IS NULL OR ae.`workbench_task_id` <> t.`id`);

-- 2. 已有 AgentExecution 的 Token 必须一对一；没有可靠执行身份的历史行保留为 legacy
SELECT tud.`task_id`, COUNT(*) AS `token_row_count`
FROM `token_usage_detail` tud
JOIN `agent_execution` ae ON ae.`workbench_task_id` = tud.`task_id`
GROUP BY tud.`task_id`
HAVING COUNT(*) <> 1;

SELECT tud.`id` AS `legacy_token_usage_id`, tud.`task_id`
FROM `token_usage_detail` tud
LEFT JOIN `agent_execution` ae ON ae.`workbench_task_id` = tud.`task_id`
WHERE ae.`id` IS NULL;

SELECT tud.`id` AS `legacy_price_snapshot_token_usage_id`, tud.`model_id`, ae.`model_config_version`,
       m.`input_price_per_million`, m.`output_price_per_million`
FROM `token_usage_detail` tud
JOIN `agent_execution` ae ON ae.`workbench_task_id` = tud.`task_id`
LEFT JOIN `model` m ON m.`id` = tud.`model_id`
WHERE ae.`model_config_version` IS NULL
   OR m.`id` IS NULL
   OR m.`input_price_per_million` IS NULL
   OR m.`output_price_per_million` IS NULL;

-- 上一结果仅记录历史快照缺失，不阻断 V23；新执行由业务代码强制完整写入价格快照。

-- 3. ChangeRequest 重改关系；唯一索引正常时第一项应恒为 0 行
SELECT cr.`rework_task_id`, COUNT(*) AS `relation_count`
FROM `change_request` cr
WHERE cr.`rework_task_id` IS NOT NULL
GROUP BY cr.`rework_task_id`
HAVING COUNT(*) <> 1;

SELECT cr.`id` AS `change_request_id`, cr.`source_task_id`, cr.`rework_task_id`,
       rework.`parent_task_id`, cr.`space_id` AS `request_space_id`,
       rework.`space_id` AS `task_space_id`, cr.`document_id` AS `request_document_id`,
       rework.`document_id` AS `task_document_id`
FROM `change_request` cr
LEFT JOIN `task` rework ON rework.`id` = cr.`rework_task_id`
WHERE cr.`rework_task_id` IS NOT NULL
  AND (rework.`id` IS NULL
       OR cr.`source_task_id` IS NULL
       OR rework.`parent_task_id` IS NULL
       OR rework.`parent_task_id` <> cr.`source_task_id`
       OR rework.`space_id` <> cr.`space_id`
       OR rework.`document_id` <> cr.`document_id`);

-- CHANGE_REQUEST_REWORK_CREATED 的 detail 应等于 rework_task_id；审计缺失或矛盾都阻断。
SELECT cr.`id` AS `change_request_id`, cr.`rework_task_id`,
       COUNT(al.`id`) AS `matching_audit_count`
FROM `change_request` cr
LEFT JOIN `audit_log` al
       ON al.`action` = 'CHANGE_REQUEST_REWORK_CREATED'
      AND al.`target_type` = 'change_request'
      AND al.`target_id` = cr.`id`
      AND BINARY al.`detail` = BINARY CAST(cr.`rework_task_id` AS CHAR)
WHERE cr.`rework_task_id` IS NOT NULL
GROUP BY cr.`id`, cr.`rework_task_id`
HAVING COUNT(al.`id`) <> 1;

SELECT al.`id` AS `audit_id`, al.`target_id` AS `change_request_id`, al.`detail`,
       cr.`rework_task_id`
FROM `audit_log` al
LEFT JOIN `change_request` cr ON cr.`id` = al.`target_id`
WHERE al.`action` = 'CHANGE_REQUEST_REWORK_CREATED'
  AND al.`target_type` = 'change_request'
  AND (cr.`id` IS NULL
       OR cr.`rework_task_id` IS NULL
       OR BINARY al.`detail` <> BINARY CAST(cr.`rework_task_id` AS CHAR));

-- TASK_RETRY 只能作为交叉证据；列出没有任何重试审计的派生 Task 供人工核查。
SELECT t.`id` AS `task_id`, t.`parent_task_id`
FROM `task` t
LEFT JOIN `audit_log` al
       ON al.`action` = 'TASK_RETRY'
      AND al.`target_type` = 'task'
      AND al.`target_id` = t.`id`
WHERE t.`parent_task_id` IS NOT NULL
GROUP BY t.`id`, t.`parent_task_id`
HAVING COUNT(al.`id`) = 0;

-- 4. 直接父链异常
SELECT child.`id` AS `task_id`, child.`parent_task_id`, 'MISSING_PARENT' AS `reason`
FROM `task` child
LEFT JOIN `task` parent ON parent.`id` = child.`parent_task_id`
WHERE child.`parent_task_id` IS NOT NULL
  AND parent.`id` IS NULL;

SELECT t.`id` AS `task_id`, t.`parent_task_id`, 'SELF_CYCLE' AS `reason`
FROM `task` t
WHERE t.`parent_task_id` = t.`id`;

/*
 * 5. 多节点循环检查（任意深度，MySQL 5.7 无递归 CTE）。
 *
 * 先执行建表与初始化，再重复执行 UPDATE，直到 affected rows = 0；最后执行结果查询。
 * UPDATE 只作用于 TEMPORARY TABLE，不修改业务数据。若运维客户端不能显示 affected rows，
 * 每次 UPDATE 后执行 SELECT COUNT(*) FROM tmp_v23_parent_walk WHERE finished = 0，直到为 0。
 */
DROP TEMPORARY TABLE IF EXISTS `tmp_v23_parent_walk`;
CREATE TEMPORARY TABLE `tmp_v23_parent_walk` (
    `origin_id` BIGINT NOT NULL,
    `current_id` BIGINT DEFAULT NULL,
    `visited_ids` LONGTEXT NOT NULL,
    `depth` BIGINT NOT NULL DEFAULT 0,
    `finished` TINYINT NOT NULL DEFAULT 0,
    `cycle_entry` BIGINT DEFAULT NULL,
    PRIMARY KEY (`origin_id`)
) ENGINE=InnoDB;

INSERT INTO `tmp_v23_parent_walk` (`origin_id`, `current_id`, `visited_ids`)
SELECT t.`id`, t.`parent_task_id`, CAST(t.`id` AS CHAR)
FROM `task` t
WHERE t.`parent_task_id` IS NOT NULL
  AND t.`parent_task_id` <> t.`id`;

-- 重复执行本 UPDATE，直到 affected rows = 0。
UPDATE `tmp_v23_parent_walk` walk
LEFT JOIN `task` current_task ON current_task.`id` = walk.`current_id`
SET walk.`cycle_entry` = CASE
        WHEN FIND_IN_SET(walk.`current_id`, walk.`visited_ids`) > 0 THEN walk.`current_id`
        ELSE walk.`cycle_entry`
    END,
    walk.`finished` = CASE
        WHEN current_task.`id` IS NULL
          OR current_task.`parent_task_id` IS NULL
          OR current_task.`parent_task_id` = current_task.`id`
          OR FIND_IN_SET(walk.`current_id`, walk.`visited_ids`) > 0 THEN 1
        ELSE 0
    END,
    walk.`visited_ids` = CASE
        WHEN current_task.`id` IS NOT NULL
          AND FIND_IN_SET(walk.`current_id`, walk.`visited_ids`) = 0
            THEN CONCAT(walk.`visited_ids`, ',', walk.`current_id`)
        ELSE walk.`visited_ids`
    END,
    walk.`current_id` = current_task.`parent_task_id`,
    walk.`depth` = walk.`depth` + 1
WHERE walk.`finished` = 0;

SELECT `origin_id` AS `affected_task_id`, `cycle_entry`, `depth`, `visited_ids`
FROM `tmp_v23_parent_walk`
WHERE `cycle_entry` IS NOT NULL
ORDER BY `cycle_entry`, `origin_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_v23_parent_walk`;
