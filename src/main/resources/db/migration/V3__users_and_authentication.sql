CREATE TABLE garden_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库自增用户主键；API 返回字符串',
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '去首尾空白并整体转小写的ASCII邮箱；不折叠点或加号',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Spring PasswordEncoder带算法前缀的PBKDF2哈希；禁止明文',
    nickname VARCHAR(30) NOT NULL DEFAULT '花园新朋友' COMMENT '用户昵称',
    avatar_url VARCHAR(2048) NULL DEFAULT NULL COMMENT '头像URL；本期不开放修改',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED；仅服务端控制',
    credential_version INT NOT NULL DEFAULT 1 COMMENT '密码重置时递增；拒绝旧会话及并发回写的旧会话',
    last_login_at DATETIME(6) NULL DEFAULT NULL COMMENT '最后成功登录时间；UTC',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间；UTC',
    updated_at DATETIME(6) NOT NULL COMMENT '修改时间；UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_garden_user_email (email),
    CONSTRAINT chk_garden_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_garden_user_version CHECK (credential_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专注花园用户；不创建默认账号';

CREATE TABLE garden_email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '验证码槽主键',
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化邮箱',
    purpose VARCHAR(24) NOT NULL COMMENT 'REGISTER/RESET_PASSWORD；邮箱及用途共同绑定',
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮箱+用途+验证码的HMAC-SHA256十六进制值；不保存明文',
    expires_at DATETIME(6) NOT NULL COMMENT '验证码过期时间；UTC；签发后10分钟',
    consumed_at DATETIME(6) NULL DEFAULT NULL COMMENT '一次性消费时间；UTC',
    failed_attempts INT NOT NULL DEFAULT 0 COMMENT '当前验证码失败次数；达到5次失效',
    created_at DATETIME(6) NOT NULL COMMENT '当前验证码签发时间；UTC；重发覆盖',
    PRIMARY KEY (id),
    UNIQUE KEY uk_garden_verification_slot (email, purpose),
    KEY idx_garden_verification_expiry (expires_at),
    CONSTRAINT chk_garden_verification_purpose CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD')),
    CONSTRAINT chk_garden_verification_attempts CHECK (failed_attempts BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每邮箱每用途一个验证码槽；重发原子替换旧码，不保存发送历史';

CREATE TABLE garden_auth_rate_limit (
    bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '限流操作和邮箱或IP的HMAC摘要',
    window_start BIGINT NOT NULL COMMENT '固定窗口开始的UTC epoch秒',
    attempts INT NOT NULL DEFAULT 0 COMMENT '已尝试请求次数；事务原子递增',
    expires_at DATETIME(6) NOT NULL COMMENT '窗口结束时间；UTC；后台批量清理',
    PRIMARY KEY (bucket_key, window_start),
    KEY idx_garden_rate_expiry (expires_at),
    CONSTRAINT chk_garden_rate_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据库共享的邮箱与IP限流计数；不依赖单机内存';
