-- 用量执行记录导出权限：观察者可查看，但不能批量导出。

INSERT INTO `permission` (`code`, `name`, `category`, `description`, `sort_order`)
VALUES ('usage:export', '导出用量记录', 'USAGE', '导出空间执行记录', 265);

INSERT INTO `space_role_permission` (`role_id`, `permission_code`)
SELECT r.`id`, 'usage:export'
FROM `space_role` r
WHERE r.`role_key` IN ('OWNER', 'EDITOR')
  AND NOT EXISTS (
      SELECT 1
      FROM `space_role_permission` srp
      WHERE srp.`role_id` = r.`id`
        AND srp.`permission_code` = 'usage:export'
  );
