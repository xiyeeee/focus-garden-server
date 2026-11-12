package com.xiye.garden.auth.dto;

import jakarta.validation.constraints.*;
import com.xiye.garden.auth.entity.VerificationPurpose;

public record LoginDTO(@NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Pattern(regexp = "[!-~]+", message = "邮箱仅支持ASCII字符") @Size(max = 254, message = "邮箱最多254个字符") String email, @NotNull(message = "密码不能为空") @Size(min = 8, max = 128, message = "密码长度为8至128个字符") String password) {
    public LoginDTO {
        if (email != null) email = email.strip();
    }
    @Override
    public String toString() { return "LoginDTO[REDACTED]"; }
}
