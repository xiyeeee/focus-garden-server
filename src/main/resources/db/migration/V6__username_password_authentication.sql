-- 保留所有现有邮箱用户；不生成默认用户名，不伪造邮箱，不修改历史迁移。
ALTER TABLE garden_user
    MODIFY COLUMN email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL
        COMMENT '规范化的邮箱；用户名注册账号为NULL；邮箱账号仍经验证码验证',
    ADD COLUMN username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL
        COMMENT '登录用户名；3至32位、字母开头、字母数字下划线；去首尾空白并转小写；旧邮箱账号为NULL',
    ADD UNIQUE KEY uk_garden_user_username (username),
    ADD CONSTRAINT chk_garden_user_identity CHECK (email IS NOT NULL OR username IS NOT NULL),
    ADD CONSTRAINT chk_garden_user_username_length CHECK (username IS NULL OR CHAR_LENGTH(username) BETWEEN 3 AND 32);
-- MySQL唯一键允许多条NULL：未使用的另一种身份不会发生唯一冲突。
-- 用户名格式与两种注册方式的互斥规则由Jakarta Validation校验；客户端不允许绑定或改写身份。
