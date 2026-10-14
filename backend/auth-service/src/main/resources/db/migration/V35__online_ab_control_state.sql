ALTER TABLE online_experiment
    ADD control_key_version VARCHAR(64) DEFAULT NULL,
    ADD control_ciphertext LONGTEXT,
    ADD control_expires_at DATETIME(3) DEFAULT NULL,
    ADD last_runtime_srm_check_at DATETIME(3) DEFAULT NULL;
ALTER TABLE online_experiment_event ADD detail_json LONGTEXT;
ALTER TABLE online_experiment_action_request ADD request_json LONGTEXT;

CREATE TABLE online_preflight_proof (
    id BIGINT NOT NULL, experiment_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dependency_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    state_version BIGINT NOT NULL,
    proof_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY(id), UNIQUE KEY uk_online_preflight_proof (proof_hash),
    KEY idx_online_preflight_expiry (experiment_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有时限的预检权威证明';
