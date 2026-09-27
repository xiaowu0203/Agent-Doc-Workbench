-- P4-04：同一报告输入可被不同重算请求复用，独立保存每个请求的幂等映射。
CREATE TABLE `experiment_report_request` (
    `id` BIGINT NOT NULL COMMENT '请求映射 ID',
    `experiment_id` BIGINT NOT NULL COMMENT 'Experiment ID',
    `space_id` BIGINT NOT NULL COMMENT '所属空间 ID',
    `request_key` VARCHAR(191) NOT NULL COMMENT '客户端重算幂等键',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '重算请求 SHA-256',
    `report_id` BIGINT NOT NULL COMMENT '关联的不可变报告 ID',
    `created_by` BIGINT NOT NULL COMMENT '请求人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_report_request_key` (`experiment_id`, `request_key`),
    KEY `idx_experiment_report_request_report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验报告重算请求幂等映射';
