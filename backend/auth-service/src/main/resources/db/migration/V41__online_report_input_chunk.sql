-- 大型冻结报告输入分块，避免单条 SQL 超过 MySQL 的请求包限制；历史输入不改写。
CREATE TABLE online_report_input_chunk (
    report_id BIGINT NOT NULL COMMENT '不可变报告身份',
    chunk_no INT NOT NULL COMMENT '从零开始的连续分块序号',
    content MEDIUMTEXT NOT NULL COMMENT '完整逻辑输入的连续片段',
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '片段 UTF-8 SHA-256',
    PRIMARY KEY (report_id, chunk_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ONLINE_V1 大型冻结输入的物理分块';
