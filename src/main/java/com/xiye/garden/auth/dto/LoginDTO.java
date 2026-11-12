package com.xiye.garden.auth.dto;

import jakarta.validation.constraints.*;

public record LoginDTO(
        @Email(message = "邮箱格式不正确")
        @Pattern(regexp = "[!-~]+", message = "邮箱仅支持ASCII字符")
        @Size(max = 254, message = "邮箱最多254个字符") String email,
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,31}", message = "用户名需为3至32位字母、数字或下划线，并以字母开头") String username,
        @NotNull(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度为8至128个字符") String password) {
    public LoginDTO {
        if (email != null) email = email.strip();
        if (username != null) username = username.strip();
    }

    @AssertTrue(message = "邮箱和用户名必须且只能填写一个")
    public boolean isIdentityValid() {
        return (email == null) != (username == null);
    }

    public String account() {
        return email != null ? email : username;
    }

    @Override
    public String toString() { return "LoginDTO[REDACTED]"; }
}
