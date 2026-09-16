/* Phase 2：业务执行记录与 OpenTelemetry Trace 的稳定关联。历史数据不回填。 */

ALTER TABLE `task`
    ADD COLUMN `trace_id` CHAR(32) DEFAULT NULL
        COMMENT '任务创建或首次派发时的 OpenTelemetry Trace ID' AFTER `agent_execution_id`,
    ADD KEY `idx_task_trace_id` (`trace_id`);

ALTER TABLE `agent_execution`
    ADD COLUMN `trace_id` CHAR(32) DEFAULT NULL
        COMMENT 'agentdoc.agent.execute 主 Span 的 OpenTelemetry Trace ID' AFTER `workbench_task_id`,
    ADD COLUMN `span_id` CHAR(16) DEFAULT NULL
        COMMENT 'agentdoc.agent.execute 主 Span ID' AFTER `trace_id`,
    ADD KEY `idx_agent_execution_trace_id` (`trace_id`);
