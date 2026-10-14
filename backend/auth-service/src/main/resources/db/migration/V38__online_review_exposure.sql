-- 只追加首次知情事实；不把缺历史访问记录解释为盲态。
CREATE TABLE online_review_exposure (
    id BIGINT NOT NULL COMMENT '不可变首次知情记录身份',
    experiment_id BIGINT NOT NULL COMMENT '实验身份',
    space_id BIGINT NOT NULL COMMENT '当前空间身份',
    actor_id BIGINT NOT NULL COMMENT '读取分组或配置的人类用户',
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '首次访问入口',
    captured_at DATETIME(3) NOT NULL COMMENT '首次访问时间',
    source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '身份与首次来源摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '保存时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_online_review_exposure_actor (experiment_id, actor_id),
    KEY idx_online_review_exposure_space (space_id, experiment_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='线上原始质量评审的已知知情事实';
