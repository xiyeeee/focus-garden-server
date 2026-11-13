package com.xiye.garden.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterDTO(
        @Email(message = "邮箱格式不正确")
        @Pattern(regexp = "[!-~]+", message = "邮箱仅支持ASCII字符")
        @Size(max = 254, message = "邮箱最多254个字符") String email,
        @NotNull(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度为8至128个字符") String password,
        @Pattern(regexp = "[0-9]{6}", message = "验证码必须为6位数字") String verificationCode,
        @Size(max = 30, message = "昵称最多30个字符")
        @Pattern(regexp = "[^@\\p{Cntrl}]+", message = "昵称不能包含@或控制字符") String nickname,
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,31}", message = "用户名需为3至32位字母、数字或下划线，并以字母开头") String username) {
    public RegisterDTO {
        if (email != null) email = email.strip();
        if (username != null) username = username.strip();
        if (nickname != null) nickname = nickname.strip().isEmpty() ? null : nickname.strip();
    }

    @AssertTrue(message = "用户名注册只填用户名和密码；邮箱注册需填写邮箱和验证码，不得同时填写用户名")
    public boolean isRegistrationValid() {
        return (email != null && username == null && verificationCode != null)
                || (username != null && email == null && verificationCode == null);
    }

    @Override
    public String toString() { return "RegisterDTO[REDACTED]"; }
}
