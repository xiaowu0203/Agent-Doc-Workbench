-- ONLINE_V1决定绑定不可变报告摘要；既有决定不伪造摘要或回填。
ALTER TABLE online_experiment ADD decision_report_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
ALTER TABLE online_experiment_report ADD KEY idx_online_report_space (space_id, experiment_id, revision);
