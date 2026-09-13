-- Agent 模板版本增加停用状态：停用版本保留历史记录，但不可新安装或升级引用。
ALTER TABLE `agent_template_version`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 DRAFT / 1 PUBLISHED / 2 DISABLED';
