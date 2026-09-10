-- Phase 6：保存外部 MCP 最近一次连接测试结果与最近一次成功发现的工具快照。

ALTER TABLE `mcp_server`
    ADD COLUMN `connection_status` VARCHAR(16) NOT NULL DEFAULT 'UNTESTED'
        COMMENT '最近连接测试状态：UNTESTED / SUCCESS / FAILED' AFTER `status`,
    ADD COLUMN `last_tested_at` DATETIME DEFAULT NULL
        COMMENT '最近一次连接测试完成时间' AFTER `connection_status`,
    ADD COLUMN `last_test_duration_ms` BIGINT DEFAULT NULL
        COMMENT '最近一次握手与工具发现总耗时，毫秒' AFTER `last_tested_at`,
    ADD COLUMN `last_test_error` VARCHAR(1000) DEFAULT NULL
        COMMENT '最近一次连接失败错误摘要' AFTER `last_test_duration_ms`,
    ADD COLUMN `discovered_tool_count` INT NOT NULL DEFAULT 0
        COMMENT '最近一次成功发现的工具数量' AFTER `last_test_error`,
    ADD COLUMN `discovered_tools_json` MEDIUMTEXT DEFAULT NULL
        COMMENT '最近一次成功发现的工具定义 JSON 快照' AFTER `discovered_tool_count`,
    ADD COLUMN `tools_discovered_at` DATETIME DEFAULT NULL
        COMMENT '当前工具快照发现时间' AFTER `discovered_tools_json`;
