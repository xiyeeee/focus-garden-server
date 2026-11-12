package com.xiye.garden.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class AuthMaintenance {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        var now = LocalDateTime.now(clock);
        jdbc.update("DELETE FROM garden_auth_rate_limit WHERE expires_at < ? LIMIT 1000", now);
        jdbc.update("DELETE FROM garden_email_verification WHERE expires_at < ? LIMIT 1000", now.minusDays(1));
    }
}
