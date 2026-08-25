CREATE TABLE `a2a_task_store` (
    `task_id`           VARCHAR(100) NOT NULL COMMENT 'A2A Task ID',
    `context_id`        VARCHAR(100) DEFAULT NULL COMMENT 'A2A Context ID',
    `state`             VARCHAR(64)  DEFAULT NULL COMMENT 'A2A Task State',
    `status_timestamp`  DATETIME     DEFAULT NULL COMMENT '协议状态时间（UTC）',
    `encrypted_payload` LONGTEXT     NOT NULL COMMENT 'AES-GCM 加密的 A2A Task JSON',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`task_id`),
    KEY `idx_a2a_task_context` (`context_id`),
    KEY `idx_a2a_task_state_time` (`state`, `status_timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A 协议任务持久化';

CREATE TABLE `a2a_push_config` (
    `config_id`         VARCHAR(100) NOT NULL COMMENT 'Push Config ID',
    `task_id`           VARCHAR(100) NOT NULL COMMENT 'A2A Task ID',
    `protocol_version`  VARCHAR(32)  DEFAULT NULL COMMENT 'A2A 协议版本',
    `encrypted_payload` LONGTEXT     NOT NULL COMMENT 'AES-GCM 加密的 Push Config JSON',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`config_id`),
    KEY `idx_a2a_push_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A2A Push Notification 配置持久化';
