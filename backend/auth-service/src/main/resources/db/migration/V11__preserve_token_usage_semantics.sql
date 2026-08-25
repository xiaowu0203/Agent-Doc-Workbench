-- 保留模型 Token 缺失语义，并记录本地估算来源；不回填历史0值，避免制造虚假的精确数据。
ALTER TABLE `agent_execution`
    MODIFY COLUMN `input_tokens` BIGINT DEFAULT NULL COMMENT '输入 Token，未获取时为 NULL',
    ADD COLUMN `input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输入 Token 是否为本地估算值' AFTER `input_tokens`,
    ADD COLUMN `cached_input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '缓存输入 Token 是否为本地估算值' AFTER `cached_input_tokens`,
    MODIFY COLUMN `output_tokens` BIGINT DEFAULT NULL COMMENT '输出 Token，未获取时为 NULL',
    ADD COLUMN `output_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输出 Token 是否为本地估算值' AFTER `output_tokens`;

ALTER TABLE `task`
    MODIFY COLUMN `tokens_used` BIGINT DEFAULT NULL COMMENT '已用 Token，无法获取时为 NULL',
    ADD COLUMN `tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '已用 Token 是否包含本地估算值' AFTER `tokens_used`;

ALTER TABLE `token_usage_detail`
    MODIFY COLUMN `input_tokens` BIGINT DEFAULT NULL COMMENT '输入 Token，未获取时为 NULL',
    ADD COLUMN `input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输入 Token 是否为本地估算值' AFTER `input_tokens`,
    ADD COLUMN `cached_input_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '缓存输入 Token 是否为本地估算值' AFTER `cached_input_tokens`,
    MODIFY COLUMN `output_tokens` BIGINT DEFAULT NULL COMMENT '输出 Token，未获取时为 NULL',
    ADD COLUMN `output_tokens_estimated` TINYINT NOT NULL DEFAULT 0 COMMENT '输出 Token 是否为本地估算值' AFTER `output_tokens`,
    MODIFY COLUMN `estimated_cost` DECIMAL(14,6) DEFAULT NULL COMMENT '预估人民币费用，Token缺失时为NULL，仅展示，可重新核算';
