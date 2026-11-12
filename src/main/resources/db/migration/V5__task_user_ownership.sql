-- 历史任务全部保留，user_id=NULL代表待人工核实归属的隔离数据；不自动绑定任何新用户。
ALTER TABLE tasks
    ADD COLUMN user_id BIGINT NULL DEFAULT NULL COMMENT '任务归属用户；历史NULL任务隔离，API不读取；新建必须取认证上下文',
    ADD CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES garden_user(id) ON DELETE RESTRICT,
    DROP INDEX idx_tasks_deleted_status_created,
    DROP INDEX idx_tasks_deleted_created,
    ADD INDEX idx_tasks_user_deleted_status_created (user_id, deleted, status, created_at, id),
    ADD INDEX idx_tasks_user_deleted_created (user_id, deleted, created_at, id);
