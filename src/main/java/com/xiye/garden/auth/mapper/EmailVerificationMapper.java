package com.xiye.garden.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiye.garden.auth.entity.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface EmailVerificationMapper extends BaseMapper<EmailVerificationEntity> {
    @Select("SELECT * FROM garden_email_verification WHERE email = #{email} AND purpose = #{purpose} FOR UPDATE")
    EmailVerificationEntity lock(@Param("email") String email, @Param("purpose") VerificationPurpose purpose);

    @Insert("""
            INSERT INTO garden_email_verification
                (email, purpose, code_hash, expires_at, failed_attempts, created_at)
            VALUES (#{email}, #{purpose}, #{hash}, #{now}, 0, #{now})
            ON DUPLICATE KEY UPDATE id = id
            """)
    void ensureSlot(@Param("email") String email, @Param("purpose") VerificationPurpose purpose,
                    @Param("hash") String hash, @Param("now") java.time.LocalDateTime now);
}
