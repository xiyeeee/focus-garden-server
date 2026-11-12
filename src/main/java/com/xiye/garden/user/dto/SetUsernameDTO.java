package com.xiye.garden.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetUsernameDTO(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,31}", message = "用户名需为3至32位字母、数字或下划线，并以字母开头")
        String username) {
    public SetUsernameDTO {
        if (username != null) username = username.strip();
    }
}
