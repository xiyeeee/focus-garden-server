-- 保留 V1 校验和：先由 V1 创建完整基础表，再增量补全任务元数据与删除策略。
-- BIGINT AUTO_INCREMENT 由 MySQL 分配主键；JSON 对外返回十进制字符串。
ALTER TABLE tasks
    COMMENT = '专注花园任务；当前为本地单用户，逻辑删除保留历史关联',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键；API 使用字符串',
    MODIFY COLUMN title VARCHAR(100) NOT NULL COMMENT '任务标题；请求去除首尾空白',
    MODIFY COLUMN description VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '任务描述；空描述使用空字符串',
    MODIFY COLUMN planned_focus_minutes INT NOT NULL COMMENT '计划专注分钟数；1至1440；由请求明确指定',
    MODIFY COLUMN status VARCHAR(20) NOT NULL COMMENT '任务状态 TODO/IN_PROGRESS/COMPLETED；与专注会话独立',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL COMMENT '服务端创建时间；UTC，微秒精度',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL COMMENT '服务端最后修改或逻辑删除时间；UTC',
    MODIFY COLUMN completed_at DATETIME(6) NULL DEFAULT NULL COMMENT '任务完成时间；UTC；重新打开任务时清空',
    ADD COLUMN plant_type VARCHAR(16) NOT NULL DEFAULT 'fern' COMMENT '下轮专注植物偏好；不是已获得的植物',
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记；0正常、1已删除；保留任务与历史关联',
    ADD CONSTRAINT chk_tasks_plant_type CHECK (plant_type IN ('fern', 'daisy', 'cactus', 'tulip')),
    ADD CONSTRAINT chk_tasks_deleted CHECK (deleted IN (0, 1)),
    DROP INDEX idx_tasks_status_created,
    ADD INDEX idx_tasks_deleted_status_created (deleted, status, created_at, id),
    ADD INDEX idx_tasks_deleted_created (deleted, created_at, id);
-- V1 的分钟数、任务状态与完成时间 CHECK 约束保持有效。
-- 不新增专注表、不级联删除任何历史数据；未来专注记录须保存任务标题/时长/植物快照。
