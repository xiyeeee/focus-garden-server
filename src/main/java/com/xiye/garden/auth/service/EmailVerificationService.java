package com.xiye.garden.auth.service;

import com.xiye.garden.auth.entity.*;
import com.xiye.garden.auth.exception.AuthProblem;
import com.xiye.garden.auth.mapper.EmailVerificationMapper;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationMapper mapper;
    private final VerificationProtector protector;
    private final VerificationMailSender mail;
    private final AuthRateLimiter limiter;
    private final Clock clock;

    @Transactional
    public void send(String email, VerificationPurpose purpose, String ip) {
        limiter.check("send:" + purpose, email, ip);
        var now = LocalDateTime.now(clock);
        String code = protector.generate();
        String hash = protector.codeHash(email, purpose, code);
        mapper.ensureSlot(email, purpose, "0".repeat(64), now);
        var slot = mapper.lock(email, purpose);
        if (!"0".repeat(64).equals(slot.getCodeHash()) && slot.getCreatedAt().plusSeconds(60).isAfter(now)) {
            long wait = Math.max(1, java.time.Duration.between(now, slot.getCreatedAt().plusSeconds(60)).toSeconds());
            throw new AuthProblem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请稍后再获取验证码", wait);
        }
        while (hash.equals(slot.getCodeHash())) {
            code = protector.generate();
            hash = protector.codeHash(email, purpose, code);
        }
        try {
            // Keep the slot locked until SMTP acceptance; any send failure rolls back the new challenge.
            mail.send(email, purpose, code);
        } catch (MailException exception) {
            throw new AuthProblem(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "邮件暂时无法发送，请稍后重试");
        }
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EmailVerificationEntity>()
                .eq("id", slot.getId()).set("code_hash", hash).set("expires_at", now.plusMinutes(10))
                .set("consumed_at", null).set("failed_attempts", 0).set("created_at", now));
    }

    // Called only inside AuthService's noRollbackFor transaction: failed attempts must be committed.
    public void consume(String email, VerificationPurpose purpose, String code) {
        var slot = mapper.lock(email, purpose);
        var now = LocalDateTime.now(clock);
        if (slot == null || slot.getConsumedAt() != null || !slot.getExpiresAt().isAfter(now) || slot.getFailedAttempts() >= 5) {
            throw invalidCode();
        }
        if (!protector.matches(slot.getCodeHash(), protector.codeHash(email, purpose, code))) {
            slot.setFailedAttempts(slot.getFailedAttempts() + 1);
            mapper.updateById(slot);
            throw invalidCode();
        }
        slot.setConsumedAt(now);
        mapper.updateById(slot);
    }

    private AuthProblem invalidCode() {
        return new AuthProblem(HttpStatus.BAD_REQUEST, "VERIFICATION_INVALID", "验证码无效或已过期，请重新获取");
    }
}
