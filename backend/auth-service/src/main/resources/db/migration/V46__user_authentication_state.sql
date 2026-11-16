-- 用户级人类认证版本；既有账号保留原密码，外部身份账号允许没有本地密码。
ALTER TABLE `user`
    ADD COLUMN `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '用户级人类认证版本' AFTER `status`,
    MODIFY COLUMN `password_hash` VARCHAR(100) NULL COMMENT 'BCrypt 密码哈希；外部身份账号可为空';
