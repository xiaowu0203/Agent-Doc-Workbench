/*
 * Phase 1：Run-compatible Execution 最小数据契约。
 *
 * 迁移要求：停止 Task、Agent、Document 写入，不支持 V22/V23 混合版本滚动写入。
 * 生产执行前必须先运行 docs/local/.../phase-01/v23-readonly-precheck.sql。
 */

ALTER TABLE `task`
    ADD COLUMN `root_task_id` BIGINT DEFAULT NULL COMMENT '逻辑工作根任务 ID；根任务指向自身' AFTER `parent_task_id`,
    ADD COLUMN `lineage_type` VARCHAR(32) DEFAULT NULL
        COMMENT '血缘类型：ORIGINAL / RERUN / REVIEW_REWORK / REPLAY / EXPERIMENT / LEGACY_UNKNOWN'
        AFTER `root_task_id`,
    ADD COLUMN `execution_mode` VARCHAR(16) DEFAULT NULL COMMENT '执行模式：LIVE / ISOLATED' AFTER `lineage_type`,
    ADD COLUMN `document_version_snapshot` BIGINT DEFAULT NULL COMMENT '创建任务时冻结的正式文档版本'
        AFTER `document_type`,
    ADD COLUMN `document_content_sha256` VARCHAR(64) DEFAULT NULL COMMENT '冻结文档正文 SHA-256'
        AFTER `document_version_snapshot`,
    ADD COLUMN `input_snapshot_schema_version` INT DEFAULT NULL COMMENT '任务输入快照 schema 版本'
        AFTER `document_content_sha256`,
    ADD COLUMN `input_snapshot_hash` VARCHAR(64) DEFAULT NULL COMMENT '任务输入快照稳定 SHA-256'
        AFTER `input_snapshot_schema_version`;

ALTER TABLE `agent_execution`
    ADD COLUMN `execution_snapshot_schema_version` INT DEFAULT NULL
        COMMENT '执行配置快照 schema 版本；1 为 legacy，2 为 Phase 1 新契约'
        AFTER `execution_snapshot_hash`;

ALTER TABLE `token_usage_detail`
    ADD COLUMN `execution_id` BIGINT DEFAULT NULL COMMENT 'AgentExecution ID，权威幂等键' AFTER `task_id`,
    ADD COLUMN `model_config_version` BIGINT DEFAULT NULL COMMENT '执行时模型配置版本' AFTER `model_id`,
    ADD COLUMN `input_price_per_million` DECIMAL(12,6) DEFAULT NULL COMMENT '冻结输入单价，元/百万 Token'
        AFTER `model_config_version`,
    ADD COLUMN `output_price_per_million` DECIMAL(12,6) DEFAULT NULL COMMENT '冻结输出单价，元/百万 Token'
        AFTER `input_price_per_million`,
    ADD COLUMN `currency` VARCHAR(3) DEFAULT NULL COMMENT '计价币种，首版 CNY' AFTER `output_price_per_million`,
    ADD COLUMN `pricing_schema_version` INT DEFAULT NULL COMMENT '计价公式 schema 版本' AFTER `currency`,
    ADD COLUMN `pricing_captured_at` DATETIME DEFAULT NULL COMMENT '价格快照捕获时间'
        AFTER `pricing_schema_version`;

DELIMITER $$

CREATE PROCEDURE `migrate_v23_run_compatible_execution`()
BEGIN
    DECLARE changed_rows BIGINT DEFAULT 1;
    DECLARE task_count BIGINT DEFAULT 0;

    /* 能关联到 AgentExecution 的 Token 账本无法唯一关联时禁止猜测或去重。
       没有对应 Task/AgentExecution 的历史统计行保留为 legacy 行，不伪造执行记录。 */
    IF EXISTS (
        SELECT tud.`task_id`
        FROM `token_usage_detail` tud
        JOIN `agent_execution` ae ON ae.`workbench_task_id` = tud.`task_id`
        GROUP BY tud.`task_id`
        HAVING COUNT(*) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V23 blocked: duplicate token rows for one task';
    END IF;

    /* 先收集父缺失、自环和重改关系冲突种子。 */
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_anomaly_seed`;
    CREATE TEMPORARY TABLE `tmp_v23_anomaly_seed` (
        `task_id` BIGINT NOT NULL,
        `reason` VARCHAR(64) NOT NULL,
        `anchor_id` BIGINT NOT NULL,
        PRIMARY KEY (`task_id`)
    ) ENGINE=InnoDB;

    INSERT INTO `tmp_v23_anomaly_seed` (`task_id`, `reason`, `anchor_id`)
    SELECT child.`id`, 'MISSING_PARENT', child.`id`
    FROM `task` child
    LEFT JOIN `task` parent ON parent.`id` = child.`parent_task_id`
    WHERE child.`parent_task_id` IS NOT NULL
      AND parent.`id` IS NULL;

    INSERT INTO `tmp_v23_anomaly_seed` (`task_id`, `reason`, `anchor_id`)
    SELECT t.`id`, 'SELF_CYCLE', t.`id`
    FROM `task` t
    WHERE t.`parent_task_id` = t.`id`
    ON DUPLICATE KEY UPDATE `reason` = VALUES(`reason`), `anchor_id` = VALUES(`anchor_id`);

    /* 重改任务存在但关系或审计矛盾时，以该任务自身作为异常 anchor。 */
    INSERT INTO `tmp_v23_anomaly_seed` (`task_id`, `reason`, `anchor_id`)
    SELECT rework.`id`, 'REWORK_RELATION_CONFLICT', rework.`id`
    FROM `change_request` cr
    JOIN `task` rework ON rework.`id` = cr.`rework_task_id`
    WHERE cr.`source_task_id` IS NULL
       OR rework.`parent_task_id` IS NULL
       OR rework.`parent_task_id` <> cr.`source_task_id`
       OR rework.`space_id` <> cr.`space_id`
       OR rework.`document_id` <> cr.`document_id`
       OR NOT EXISTS (
            SELECT 1
            FROM `audit_log` al
            WHERE al.`action` = 'CHANGE_REQUEST_REWORK_CREATED'
              AND al.`target_type` = 'change_request'
              AND al.`target_id` = cr.`id`
               AND BINARY al.`detail` = BINARY CAST(cr.`rework_task_id` AS CHAR)
       )
       OR EXISTS (
            SELECT 1
            FROM `audit_log` al
            WHERE al.`action` = 'CHANGE_REQUEST_REWORK_CREATED'
              AND al.`target_type` = 'change_request'
              AND al.`target_id` = cr.`id`
               AND BINARY al.`detail` <> BINARY CAST(cr.`rework_task_id` AS CHAR)
       )
    ON DUPLICATE KEY UPDATE `reason` = VALUES(`reason`), `anchor_id` = VALUES(`anchor_id`);

    /* rework_task_id 指向不存在的 Task 时没有可标记节点，必须阻断修复。 */
    IF EXISTS (
        SELECT 1
        FROM `change_request` cr
        LEFT JOIN `task` rework ON rework.`id` = cr.`rework_task_id`
        WHERE cr.`rework_task_id` IS NOT NULL
          AND rework.`id` IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V23 blocked: ChangeRequest references missing rework Task';
    END IF;

    /* MySQL 5.7 无递归 CTE：逐行沿父链迭代，visited_ids 保证环外后代也能收敛。 */
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

    SELECT COUNT(*) INTO task_count FROM `task`;
    SET changed_rows = 1;
    WHILE changed_rows > 0 DO
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
        SET changed_rows = ROW_COUNT();
        IF EXISTS (SELECT 1 FROM `tmp_v23_parent_walk` WHERE `depth` > task_count + 1) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V23 blocked: parent walk did not converge';
        END IF;
    END WHILE;

    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_cycle_member`;
    CREATE TEMPORARY TABLE `tmp_v23_cycle_member` (
        `task_id` BIGINT NOT NULL,
        `visited_ids` LONGTEXT NOT NULL,
        PRIMARY KEY (`task_id`)
    ) ENGINE=InnoDB;

    INSERT INTO `tmp_v23_cycle_member` (`task_id`, `visited_ids`)
    SELECT walk.`origin_id`, walk.`visited_ids`
    FROM `tmp_v23_parent_walk` walk
    WHERE walk.`cycle_entry` = walk.`origin_id`;

    /* MySQL 5.7 不允许同一 TEMPORARY TABLE 在单条语句中被重复打开。 */
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_cycle_peer`;
    CREATE TEMPORARY TABLE `tmp_v23_cycle_peer` LIKE `tmp_v23_cycle_member`;
    INSERT INTO `tmp_v23_cycle_peer` (`task_id`, `visited_ids`)
    SELECT `task_id`, `visited_ids` FROM `tmp_v23_cycle_member`;

    INSERT INTO `tmp_v23_anomaly_seed` (`task_id`, `reason`, `anchor_id`)
    SELECT member.`task_id`, 'CYCLE', MIN(peer.`task_id`)
    FROM `tmp_v23_cycle_member` member
    JOIN `tmp_v23_cycle_peer` peer
      ON FIND_IN_SET(peer.`task_id`, member.`visited_ids`) > 0
    GROUP BY member.`task_id`
    ON DUPLICATE KEY UPDATE `reason` = VALUES(`reason`), `anchor_id` = VALUES(`anchor_id`);

    /* 从每个节点向上寻找最先遇到的异常 seed；正常链落到最早根。 */
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_resolution`;
    CREATE TEMPORARY TABLE `tmp_v23_resolution` (
        `task_id` BIGINT NOT NULL,
        `current_id` BIGINT NOT NULL,
        `root_id` BIGINT DEFAULT NULL,
        `anomaly_reason` VARCHAR(64) DEFAULT NULL,
        `depth` BIGINT NOT NULL DEFAULT 0,
        `finished` TINYINT NOT NULL DEFAULT 0,
        PRIMARY KEY (`task_id`)
    ) ENGINE=InnoDB;

    INSERT INTO `tmp_v23_resolution` (`task_id`, `current_id`)
    SELECT t.`id`, t.`id` FROM `task` t;

    SET changed_rows = 1;
    WHILE changed_rows > 0 DO
        UPDATE `tmp_v23_resolution` resolution
        JOIN `task` current_task ON current_task.`id` = resolution.`current_id`
        LEFT JOIN `tmp_v23_anomaly_seed` seed ON seed.`task_id` = resolution.`current_id`
        SET resolution.`root_id` = CASE
                WHEN seed.`task_id` IS NOT NULL THEN seed.`anchor_id`
                WHEN current_task.`parent_task_id` IS NULL THEN current_task.`id`
                ELSE resolution.`root_id`
            END,
            resolution.`anomaly_reason` = CASE
                WHEN seed.`task_id` IS NOT NULL THEN seed.`reason`
                ELSE resolution.`anomaly_reason`
            END,
            resolution.`finished` = CASE
                WHEN seed.`task_id` IS NOT NULL OR current_task.`parent_task_id` IS NULL THEN 1
                ELSE 0
            END,
            resolution.`current_id` = CASE
                WHEN seed.`task_id` IS NOT NULL OR current_task.`parent_task_id` IS NULL
                    THEN resolution.`current_id`
                ELSE current_task.`parent_task_id`
            END,
            resolution.`depth` = resolution.`depth` + 1
        WHERE resolution.`finished` = 0;
        SET changed_rows = ROW_COUNT();
        IF EXISTS (SELECT 1 FROM `tmp_v23_resolution` WHERE `depth` > task_count + 1) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V23 blocked: ancestry resolution did not converge';
        END IF;
    END WHILE;

    IF EXISTS (SELECT 1 FROM `tmp_v23_resolution` WHERE `finished` = 0 OR `root_id` IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V23 blocked: unresolved Task ancestry';
    END IF;

    UPDATE `task` t
    JOIN `tmp_v23_resolution` resolution ON resolution.`task_id` = t.`id`
    SET t.`root_task_id` = resolution.`root_id`,
        t.`lineage_type` = CASE
            WHEN resolution.`anomaly_reason` IS NOT NULL THEN 'LEGACY_UNKNOWN'
            WHEN t.`parent_task_id` IS NULL THEN 'ORIGINAL'
            WHEN EXISTS (
                SELECT 1 FROM `change_request` cr
                WHERE cr.`rework_task_id` = t.`id`
                  AND cr.`source_task_id` = t.`parent_task_id`
            ) THEN 'REVIEW_REWORK'
            ELSE 'RERUN'
        END,
        t.`execution_mode` = 'LIVE';

    UPDATE `agent_execution`
    SET `execution_snapshot_schema_version` = 1
    WHERE `execution_snapshot_hash` IS NOT NULL;

    UPDATE `token_usage_detail` tud
    JOIN `agent_execution` ae ON ae.`workbench_task_id` = tud.`task_id`
    JOIN `model` m ON m.`id` = tud.`model_id`
    SET tud.`execution_id` = ae.`id`,
        tud.`model_config_version` = ae.`model_config_version`,
        tud.`input_price_per_million` = CASE
            WHEN ae.`model_config_version` IS NOT NULL
             AND m.`input_price_per_million` IS NOT NULL
             AND m.`output_price_per_million` IS NOT NULL
                THEN m.`input_price_per_million`
            ELSE NULL
        END,
        tud.`output_price_per_million` = CASE
            WHEN ae.`model_config_version` IS NOT NULL
             AND m.`input_price_per_million` IS NOT NULL
             AND m.`output_price_per_million` IS NOT NULL
                THEN m.`output_price_per_million`
            ELSE NULL
        END,
        tud.`currency` = CASE
            WHEN ae.`model_config_version` IS NOT NULL
             AND m.`input_price_per_million` IS NOT NULL
             AND m.`output_price_per_million` IS NOT NULL
                THEN 'CNY'
            ELSE NULL
        END,
        tud.`pricing_schema_version` = CASE
            WHEN ae.`model_config_version` IS NOT NULL
             AND m.`input_price_per_million` IS NOT NULL
             AND m.`output_price_per_million` IS NOT NULL
                THEN 1
            ELSE NULL
        END,
        tud.`pricing_captured_at` = CASE
            WHEN ae.`model_config_version` IS NOT NULL
             AND m.`input_price_per_million` IS NOT NULL
             AND m.`output_price_per_million` IS NOT NULL
                THEN NOW()
            ELSE NULL
        END,
        tud.`estimated_cost` = CASE
            WHEN ae.`model_config_version` IS NULL
              OR m.`input_price_per_million` IS NULL
              OR m.`output_price_per_million` IS NULL
                THEN tud.`estimated_cost`
            WHEN tud.`input_tokens` IS NULL OR tud.`output_tokens` IS NULL THEN NULL
            ELSE ROUND((tud.`input_tokens` * m.`input_price_per_million`
                      + tud.`output_tokens` * m.`output_price_per_million`) / 1000000, 6)
        END;

    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_resolution`;
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_cycle_peer`;
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_cycle_member`;
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_parent_walk`;
    DROP TEMPORARY TABLE IF EXISTS `tmp_v23_anomaly_seed`;
END$$

DELIMITER ;

CALL `migrate_v23_run_compatible_execution`();
DROP PROCEDURE `migrate_v23_run_compatible_execution`;

ALTER TABLE `task`
    MODIFY COLUMN `root_task_id` BIGINT NOT NULL COMMENT '逻辑工作根任务 ID；根任务指向自身',
    MODIFY COLUMN `lineage_type` VARCHAR(32) NOT NULL
        COMMENT '血缘类型：ORIGINAL / RERUN / REVIEW_REWORK / REPLAY / EXPERIMENT / LEGACY_UNKNOWN',
    MODIFY COLUMN `execution_mode` VARCHAR(16) NOT NULL COMMENT '执行模式：LIVE / ISOLATED',
    ADD KEY `idx_task_root_created` (`root_task_id`, `created_at`, `id`),
    ADD KEY `idx_task_parent` (`parent_task_id`);

ALTER TABLE `token_usage_detail`
    MODIFY COLUMN `execution_id` BIGINT DEFAULT NULL COMMENT 'AgentExecution ID；历史无法关联的 legacy 行保留 NULL',
    MODIFY COLUMN `model_config_version` BIGINT DEFAULT NULL COMMENT '执行时模型配置版本；legacy 行为空',
    MODIFY COLUMN `input_price_per_million` DECIMAL(12,6) DEFAULT NULL COMMENT '冻结输入单价；legacy 行为空',
    MODIFY COLUMN `output_price_per_million` DECIMAL(12,6) DEFAULT NULL COMMENT '冻结输出单价；legacy 行为空',
    MODIFY COLUMN `currency` VARCHAR(3) DEFAULT NULL COMMENT '计价币种；legacy 行为空',
    MODIFY COLUMN `pricing_schema_version` INT DEFAULT NULL COMMENT '计价公式 schema 版本；legacy 行为空',
    MODIFY COLUMN `pricing_captured_at` DATETIME DEFAULT NULL COMMENT '价格快照捕获时间；legacy 行为空',
    MODIFY COLUMN `estimated_cost` DECIMAL(14,6) DEFAULT NULL
        COMMENT '按本行冻结价格快照计算的预估费用',
    ADD UNIQUE KEY `uk_token_usage_execution` (`execution_id`);

/* 最终完整性校验由领域创建、调度入口及迁移集成测试共同兜底。 */
