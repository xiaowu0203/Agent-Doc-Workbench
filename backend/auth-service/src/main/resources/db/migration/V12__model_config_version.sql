-- ChatModel 缓存按模型配置版本隔离；历史模型从版本1开始。
ALTER TABLE `model`
    ADD COLUMN `config_version` BIGINT NOT NULL DEFAULT 1 COMMENT '模型配置版本，每次影响模型调用配置的修改递增' AFTER `options_json`;
