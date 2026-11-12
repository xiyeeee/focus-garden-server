package com.xiye.garden.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("garden_email_verification")
public class EmailVerificationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private VerificationPurpose purpose;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private Integer failedAttempts;
    private LocalDateTime createdAt;
}
