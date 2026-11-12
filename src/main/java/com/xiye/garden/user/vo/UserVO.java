package com.xiye.garden.user.vo;

import com.xiye.garden.user.entity.UserEntity;
import java.time.Instant;
import java.time.ZoneOffset;

public record UserVO(String id, String email, String nickname, String avatarUrl,
                     Instant lastLoginAt, Instant createdAt, Instant updatedAt) {
    public static UserVO from(UserEntity user) {
        return new UserVO(user.getId().toString(), user.getEmail(), user.getNickname(), user.getAvatarUrl(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toInstant(ZoneOffset.UTC),
                user.getCreatedAt().toInstant(ZoneOffset.UTC), user.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }
}
