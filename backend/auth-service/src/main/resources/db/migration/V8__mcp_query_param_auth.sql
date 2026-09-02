-- Phase 6：支持将外部 MCP API Key 作为加密的 URL query 参数发送。
-- endpoint_url 继续只保存无 query 的安全基础地址，秘密值沿用 encrypted_auth_token 加密列。

ALTER TABLE `mcp_server`
    ADD COLUMN `auth_param_name` VARCHAR(64) DEFAULT NULL
        COMMENT 'QUERY_PARAM 认证使用的 URL query 参数名' AFTER `auth_type`;
