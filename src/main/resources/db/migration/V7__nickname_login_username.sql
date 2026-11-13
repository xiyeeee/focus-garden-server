-- 邮箱注册复用昵称为登录名；保留username字段作为唯一身份，不按重复昵称模糊匹配账号。
ALTER TABLE garden_user
    DROP CHECK chk_garden_user_username_length,
    MODIFY COLUMN username VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT NULL
        COMMENT '唯一登录名；邮箱注册时由显式昵称去首尾空格并转小写生成；支持中文；已有值不改写',
    ADD CONSTRAINT chk_garden_user_username_length
        CHECK (username IS NULL OR CHAR_LENGTH(username) BETWEEN 1 AND 32);

-- 仅回填唯一、非默认的历史昵称。重复昵称和已被其他账号占用的登录名跳过，不能任意选择账号。
-- 先物化候选，避免更新目标表的子查询限制；临时表仅在当前迁移连接存在。
CREATE TEMPORARY TABLE garden_nickname_login_candidates (
    id BIGINT NOT NULL PRIMARY KEY,
    username VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE
);

INSERT INTO garden_nickname_login_candidates (id, username)
SELECT MIN(id), LOWER(TRIM(nickname)) COLLATE utf8mb4_bin
FROM garden_user
WHERE CHAR_LENGTH(TRIM(nickname)) BETWEEN 1 AND 30
  AND TRIM(nickname) <> '花园新朋友'
  AND NOT REGEXP_LIKE(nickname, '[@[:cntrl:]]')
GROUP BY LOWER(TRIM(nickname)) COLLATE utf8mb4_bin
HAVING COUNT(*) = 1;

DELETE candidate
FROM garden_nickname_login_candidates candidate
JOIN garden_user existing
  ON existing.username = candidate.username AND existing.id <> candidate.id;

UPDATE garden_user target
JOIN garden_nickname_login_candidates candidate ON candidate.id = target.id
SET target.username = candidate.username
WHERE target.username IS NULL;

DROP TEMPORARY TABLE garden_nickname_login_candidates;
