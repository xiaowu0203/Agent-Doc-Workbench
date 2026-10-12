-- 仅实际访问隐藏完整原始评审上下文后生成证明，历史实验不补造证明。
CREATE TABLE online_blind_review_receipt (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, space_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL, execution_id BIGINT NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_evidence_type VARCHAR(32) NOT NULL,
    original_evidence_id BIGINT NOT NULL,
    original_evidence_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version INT NOT NULL,
    captured_at DATETIME(3) NOT NULL,
    source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_online_blind_receipt (assignment_id,actor_id,original_evidence_type),
    KEY idx_online_blind_receipt_experiment (experiment_id,actor_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='隐藏原始评审的服务器资格证明，不证明站外知情';
