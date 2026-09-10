-- 用量看板：区分空间单任务执行上限与月度累计预算。
ALTER TABLE `space`
    ADD COLUMN `monthly_token_budget` BIGINT DEFAULT NULL
        COMMENT '空间月度 Token 预算，仅用于用量提示，不参与单任务熔断'
        AFTER `token_budget`;

ALTER TABLE `token_usage_detail`
    ADD KEY `idx_tud_space_call_time` (`space_id`, `call_time`);

ALTER TABLE `agent_execution_tool_call`
    ADD KEY `idx_tool_call_started_at` (`started_at`);

ALTER TABLE `audit_log`
    ADD KEY `idx_audit_space_created` (`space_id`, `created_at`);
