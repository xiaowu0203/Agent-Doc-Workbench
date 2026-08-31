-- Phase 5：同步默认空间角色的保护状态和 VIEWER 权限边界。
-- V2 已执行环境不可回改，这里通过增量迁移修正既有数据。

UPDATE `space_role`
SET `protected_role` = CASE `role_key` WHEN 'OWNER' THEN 1 ELSE 0 END
WHERE `role_key` IN ('OWNER', 'EDITOR', 'VIEWER');

DELETE srp
FROM `space_role_permission` srp
JOIN `space_role` sr ON sr.`id` = srp.`role_id`
WHERE sr.`role_key` = 'VIEWER'
  AND srp.`permission_code` IN ('member:read', 'role:read');
