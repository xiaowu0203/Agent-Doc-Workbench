-- MCP 模板版本增加停用状态；已安装空间实例不受影响。
ALTER TABLE `mcp_template_version`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 DRAFT / 1 PUBLISHED / 2 DISABLED';
