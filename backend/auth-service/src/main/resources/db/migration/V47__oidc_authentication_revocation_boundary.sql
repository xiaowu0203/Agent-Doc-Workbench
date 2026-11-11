ALTER TABLE `user`
    ADD COLUMN `oidc_auth_not_before` BIGINT NOT NULL DEFAULT 0 COMMENT 'OIDC认证拒绝边界（UTC epoch秒），auth_time须严格大于此值' AFTER `auth_version`;
