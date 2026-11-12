-- 管理员手工执行；仅创建开发库，不创建账号、不包含密码。
-- mysql -h localhost -u <管理员账号> -p < sql/00_create_database.sql
CREATE DATABASE IF NOT EXISTS focus_garden
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
