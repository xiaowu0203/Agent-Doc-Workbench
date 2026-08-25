-- 修正多厂商模型设计落地后 model 表字段注释与实际含义不一致的问题。
ALTER TABLE `model`
    MODIFY COLUMN `provider` VARCHAR(100) NOT NULL
        COMMENT '模型业务提供商编码：openai / anthropic / google-gemini / deepseek / zhipu-glm / alibaba-qwen / xiaomi-mimo / openai-compatible；兼容旧值 dashscope / ollama',
    MODIFY COLUMN `model_key` VARCHAR(100) NOT NULL
        COMMENT '模型调用标识，传递给模型适配器作为模型名称',
    MODIFY COLUMN `display_name` VARCHAR(100) NOT NULL
        COMMENT '模型前端展示名称',
    MODIFY COLUMN `official_url` VARCHAR(255) DEFAULT NULL
        COMMENT '模型官网或官方文档地址',
    MODIFY COLUMN `base_url` VARCHAR(500) DEFAULT NULL
        COMMENT '模型服务 API 基础地址，留空时使用供应商默认地址',
    MODIFY COLUMN `encrypted_api_key` TEXT DEFAULT NULL
        COMMENT 'AES-GCM 加密后的模型 API Key',
    MODIFY COLUMN `adapter_type` VARCHAR(64) DEFAULT NULL
        COMMENT '模型调用适配器类型：openai-chat / openai-compatible / anthropic-messages / google-genai',
    MODIFY COLUMN `options_json` TEXT DEFAULT NULL
        COMMENT '适配器扩展配置 JSON，按具体适配器使用',
    MODIFY COLUMN `context_window` BIGINT DEFAULT NULL
        COMMENT '模型上下文窗口大小，仅用于模型元数据',
    MODIFY COLUMN `max_output_tokens` BIGINT DEFAULT NULL
        COMMENT '模型允许的最大输出 Token 数',
    MODIFY COLUMN `input_price_per_million` DECIMAL(12,6) DEFAULT 0.000000
        COMMENT '输入单价，元/百万 Token，仅用于费用预估',
    MODIFY COLUMN `output_price_per_million` DECIMAL(12,6) DEFAULT 0.000000
        COMMENT '输出单价，元/百万 Token，仅用于费用预估',
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1
        COMMENT '模型状态：1 启用 / 0 禁用',
    MODIFY COLUMN `description` TEXT DEFAULT NULL
        COMMENT '模型备注说明';
