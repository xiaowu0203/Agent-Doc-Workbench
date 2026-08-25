-- 模型供应商与实际调用协议解耦，兼容历史 model 数据。
ALTER TABLE `model`
    ADD COLUMN `adapter_type` VARCHAR(64) DEFAULT NULL COMMENT '模型适配器类型：openai-chat / openai-compatible / anthropic-messages / google-genai' AFTER `provider`,
    ADD COLUMN `options_json` TEXT DEFAULT NULL COMMENT '适配器扩展配置 JSON' AFTER `encrypted_api_key`;

UPDATE `model`
SET `adapter_type` = CASE `provider`
    WHEN 'openai' THEN 'openai-chat'
    WHEN 'anthropic' THEN 'anthropic-messages'
    WHEN 'gemini' THEN 'google-genai'
    WHEN 'google-gemini' THEN 'google-genai'
    ELSE 'openai-compatible'
END
WHERE `adapter_type` IS NULL;
