-- 将单个读取区间升级为多个关注区域；旧列暂时保留用于迁移核验，不再由新代码使用。

ALTER TABLE `task`
    ADD COLUMN `focus_regions_json` TEXT DEFAULT NULL COMMENT '关注区域 JSON；RANGES 时同时作为读取白名单' AFTER `read_scope`;

UPDATE `task`
SET `focus_regions_json` = JSON_ARRAY(JSON_OBJECT(
        'start', `read_start`,
        'length', `read_end` - `read_start`,
        'textPreview', NULL,
        'instruction', NULL)),
    `read_scope` = 'RANGES'
WHERE `read_scope` = 'RANGE'
  AND `read_start` IS NOT NULL
  AND `read_end` IS NOT NULL
  AND `read_end` > `read_start`;

UPDATE `task`
SET `read_scope` = 'RANGES'
WHERE `read_scope` = 'RANGE';

ALTER TABLE `task_draft`
    ADD COLUMN `focus_regions_json` TEXT DEFAULT NULL COMMENT '关注区域 JSON' AFTER `read_scope`;

UPDATE `task_draft`
SET `focus_regions_json` = JSON_ARRAY(JSON_OBJECT(
        'start', `read_start`,
        'length', `read_length`,
        'textPreview', NULL,
        'instruction', NULL)),
    `read_scope` = 'RANGES'
WHERE `read_scope` = 'RANGE'
  AND `read_start` IS NOT NULL
  AND `read_length` IS NOT NULL
  AND `read_length` > 0;

UPDATE `task_draft`
SET `read_scope` = 'RANGES'
WHERE `read_scope` = 'RANGE';
