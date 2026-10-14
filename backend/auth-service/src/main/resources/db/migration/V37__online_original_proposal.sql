-- P6-03：Task 所属领域在提交事务内冻结原始提案，不回填审批后的内容。
CREATE TABLE task_online_original_proposal (
    id BIGINT NOT NULL COMMENT '原始 ChangeRequest ID',
    task_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    experiment_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    base_version BIGINT NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    identity_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_changes LONGTEXT NOT NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_online_original_proposal_task (task_id, id),
    KEY idx_online_original_proposal_space (space_id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线上审批前原始提案，追加后不可覆盖';
