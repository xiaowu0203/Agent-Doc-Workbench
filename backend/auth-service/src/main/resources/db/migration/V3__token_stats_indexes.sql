-- ============================================================
-- Agent-Doc-Workbench 数据库迁移脚本 V3
-- 说明：Token 统计页面查询索引补充（设计依据 docs/database-design.md「查询索引」）
--  1. token_usage            折线图按"空间 + 日期范围"查询 → (space_id, usage_date)
--  2. token_daily_snapshot   今日卡片取"同空间同日最新快照" → (space_id, snapshot_date, created_at)
--     新索引完全覆盖 V2 的 idx_snap_space_date (space_id, snapshot_date)，故替换之，避免冗余索引
-- MySQL 5.7 兼容：5.7 不支持降序索引（DESC 会被忽略），最新快照查询走索引反向扫描，
--     故 DDL 中不写 DESC，由查询 ORDER BY created_at DESC 触发反向扫描。
-- ============================================================

-- ----------------------------
-- 1. token_usage：折线图按空间 + 日期范围过滤
-- ----------------------------
ALTER TABLE `token_usage`
    ADD KEY `idx_tu_space_date` (`space_id`, `usage_date`);

-- ----------------------------
-- 2. token_daily_snapshot：今日卡片取同空间同日最新快照（替换旧索引）
-- ----------------------------
ALTER TABLE `token_daily_snapshot`
    DROP KEY `idx_snap_space_date`,
    ADD KEY `idx_snap_space_date_created` (`space_id`, `snapshot_date`, `created_at`);
