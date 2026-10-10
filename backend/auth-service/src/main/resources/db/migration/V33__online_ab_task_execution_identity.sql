-- 仅追加线上执行身份；V1-V32 的已定义契约不回写。
ALTER TABLE task
    ADD online_experiment_id BIGINT DEFAULT NULL,
    ADD online_assignment_id BIGINT DEFAULT NULL,
    ADD online_binding_schema_version INT DEFAULT NULL,
    ADD online_binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD online_slot_generation BIGINT DEFAULT NULL,
    ADD online_slot_permit_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD online_wait_capability LONGTEXT,
    ADD KEY idx_task_online_experiment (online_experiment_id,id);

ALTER TABLE agent_execution
    ADD online_experiment_id BIGINT DEFAULT NULL,
    ADD online_assignment_id BIGINT DEFAULT NULL,
    ADD online_binding_schema_version INT DEFAULT NULL,
    ADD online_binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD online_slot_generation BIGINT DEFAULT NULL,
    ADD online_slot_permit_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    ADD KEY idx_execution_online_experiment (online_experiment_id,workbench_task_id);
