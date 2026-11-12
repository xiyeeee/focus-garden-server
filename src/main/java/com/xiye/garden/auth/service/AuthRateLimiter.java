package com.xiye.garden.auth.service;

import com.xiye.garden.auth.exception.AuthProblem;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final VerificationProtector protector;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = AuthProblem.class)
    public void check(String operation, String identity, String ip) {
        switch (operation) {
            case "send:REGISTER", "send:RESET_PASSWORD" -> {
                hit(operation + ":cooldown:" + identity, 1, 60);
                hit(operation + ":email:" + identity, 5, 3600);
                hit("send:ip:" + ip, 20, 3600);
            }
            case "login" -> {
                hit("login:account:" + identity, 10, 900);
                hit("login:ip:" + ip, 50, 900);
            }
            case "register" -> {
                hit("register:username:" + identity, 5, 3600);
                hit("register:ip:" + ip, 20, 3600);
            }
            case "verify" -> {
                hit("verify:email:" + identity, 10, 900);
                hit("verify:ip:" + ip, 30, 900);
            }
            default -> throw new IllegalArgumentException("未知的限流操作");
        }
    }

    private void hit(String dimension, int maximum, int windowSeconds) {
        long now = clock.instant().getEpochSecond();
        long start = now - now % windowSeconds;
        String key = protector.digest("rate:" + dimension);
        jdbc.update("""
                INSERT INTO garden_auth_rate_limit (bucket_key, window_start, attempts, expires_at)
                VALUES (?, ?, 1, ?) ON DUPLICATE KEY UPDATE attempts = attempts + 1
                """, key, start, LocalDateTime.ofInstant(Instant.ofEpochSecond(start + windowSeconds), ZoneOffset.UTC));
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM garden_auth_rate_limit WHERE bucket_key = ? AND window_start = ?",
                Integer.class, key, start);
        if (attempts != null && attempts > maximum) {
            throw new AuthProblem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁，请稍后重试", start + windowSeconds - now);
        }
    }
}
