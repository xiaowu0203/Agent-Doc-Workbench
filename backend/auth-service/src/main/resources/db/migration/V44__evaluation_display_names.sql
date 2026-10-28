-- 仅增加可选展示名称；历史身份、执行快照和报告哈希保持不变。
ALTER TABLE `evaluation_run`
    ADD COLUMN `name` VARCHAR(200) DEFAULT NULL COMMENT '用户创建时填写的可选运行名称' AFTER `space_id`;

ALTER TABLE `experiment`
    ADD COLUMN `name` VARCHAR(200) DEFAULT NULL COMMENT '用户创建时填写的可选离线实验名称' AFTER `space_id`;
