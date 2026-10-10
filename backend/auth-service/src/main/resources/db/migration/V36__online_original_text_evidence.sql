-- P6-03：Agent 所属领域的不可变最终文本；不从历史摘要回填。
CREATE TABLE agent_online_original_text (
    id BIGINT NOT NULL COMMENT 'Execution ID，同时作为文本证据身份',
    task_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    experiment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    identity_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_text LONGTEXT NOT NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_original_text_task (task_id),
    KEY idx_agent_original_text_space (space_id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线上 Agent 最终文本，追加后不可覆盖';

ALTER TABLE online_evaluation_attempt ADD created_by BIGINT DEFAULT NULL COMMENT '评价发起人，历史尝试为空';
