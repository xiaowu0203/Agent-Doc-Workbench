-- 执行模式只读投影：不改写历史执行快照、哈希与 Token 账本。
ALTER TABLE agent_execution
    ADD COLUMN execution_mode VARCHAR(16) NULL COMMENT '工作台执行模式只读投影，来源缺失时未知';

UPDATE agent_execution e
JOIN task t ON t.id = e.workbench_task_id AND t.space_id = e.space_id
SET e.execution_mode = t.execution_mode
WHERE t.execution_mode IN ('LIVE', 'ISOLATED');
