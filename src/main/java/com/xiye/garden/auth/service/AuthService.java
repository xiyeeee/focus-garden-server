package com.xiye.garden.auth.service;

import com.xiye.garden.auth.dto.*;
import com.xiye.garden.auth.entity.VerificationPurpose;
import com.xiye.garden.auth.exception.AuthProblem;
import com.xiye.garden.auth.security.GardenPrincipal;
import com.xiye.garden.user.entity.UserEntity;
import com.xiye.garden.user.mapper.UserMapper;
import com.xiye.garden.user.vo.UserVO;
import java.time.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserMapper users;
    private final EmailVerificationService verification;
    private final AuthRateLimiter limiter;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserMapper users, EmailVerificationService verification, AuthRateLimiter limiter,
                       PasswordEncoder passwords, JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.verification = verification;
        this.limiter = limiter;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.clock = clock;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = AuthProblem.class)
    public UserVO register(RegisterDTO request, String ip) {
        String email = EmailAddress.normalize(request.email());
        limiter.check("verify", email, ip);
        verification.consume(email, VerificationPurpose.REGISTER, request.verificationCode());
        var now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? "花园新朋友" : request.nickname().strip());
        user.setStatus("ACTIVE");
        user.setCredentialVersion(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            users.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new AuthProblem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "该邮箱已注册，请直接登录");
        }
        return UserVO.from(user);
    }

    @Transactional
    public GardenPrincipal login(LoginDTO request, String ip) {
        String email = EmailAddress.normalize(request.email());
        limiter.check("login", email, ip);
        var user = users.lockByEmail(email);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !"ACTIVE".equals(user.getStatus())) {
            throw new AuthProblem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码错误");
        }
        var now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        users.updateById(user);
        return new GardenPrincipal(user.getId(), user.getCredentialVersion());
    }

    @Transactional(noRollbackFor = AuthProblem.class)
    public void resetPassword(ResetPasswordDTO request, String ip) {
        String email = EmailAddress.normalize(request.email());
        limiter.check("verify", email, ip);
        verification.consume(email, VerificationPurpose.RESET_PASSWORD, request.verificationCode());
        var user = users.lockByEmail(email);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new AuthProblem(HttpStatus.BAD_REQUEST, "VERIFICATION_INVALID", "验证码无效或已过期，请重新获取");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        user.setUpdatedAt(LocalDateTime.now(clock));
        users.updateById(user);
        // Attributes cascade from the official Spring Session schema.
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", user.getId().toString());
    }

    public UserVO user(long id) {
        var user = users.selectById(id);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new AuthProblem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录或重新登录");
        }
        return UserVO.from(user);
    }
}
