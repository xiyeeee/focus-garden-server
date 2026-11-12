-- 仅管理员在已执行V5后手动运行；不要放入自动迁移。
-- 只填入经人工核实的task_id/user_id逐项映射；默认空映射，不分配任何任务。
USE focus_garden;
CREATE TEMPORARY TABLE verified_task_ownership (
    task_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL
);
-- 在下方加入经过核实的映射，例如：
-- INSERT INTO verified_task_ownership (task_id, user_id) VALUES (已核实任务ID, 已核实用户ID);
START TRANSACTION;
UPDATE tasks task
JOIN verified_task_ownership mapping ON mapping.task_id = task.id
JOIN garden_user owner ON owner.id = mapping.user_id
SET task.user_id = owner.id, task.updated_at = UTC_TIMESTAMP(6)
WHERE task.user_id IS NULL;
SELECT COUNT(*) AS remaining_quarantined_tasks FROM tasks WHERE user_id IS NULL;
COMMIT;
DROP TEMPORARY TABLE verified_task_ownership;
