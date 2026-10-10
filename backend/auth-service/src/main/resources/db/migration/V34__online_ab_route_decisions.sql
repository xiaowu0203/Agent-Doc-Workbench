-- Evaluation 持有唯一 Task 路由决定，与接受/预留同事务提交。
CREATE TABLE online_task_route (
    id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    route_json LONGTEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_online_route_request (space_id,actor_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参与和不参与的不可变路由决定';
