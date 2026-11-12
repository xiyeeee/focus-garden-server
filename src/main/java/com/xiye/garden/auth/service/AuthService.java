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
        if (request.username() != null) {
            String username = AccountIdentifier.normalize(request.username());
            limiter.check("register", username, ip);
            return createUser(null, username, request.password(), request.nickname());
        }
        String email = AccountIdentifier.normalize(request.email());
        limiter.check("verify", email, ip);
        verification.consume(email, VerificationPurpose.REGISTER, request.verificationCode());
        return createUser(email, null, request.password(), request.nickname());
    }

    private UserVO createUser(String email, String username, String password, String nickname) {
        var now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var user = new UserEntity();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwords.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? "花园新朋友" : nickname.strip());
        user.setStatus("ACTIVE");
        user.setCredentialVersion(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            users.insert(user);
        } catch (DuplicateKeyException exception) {
            if (username != null) {
                throw new AuthProblem(HttpStatus.CONFLICT, "USERNAME_ALREADY_REGISTERED", "该用户名已注册，请更换用户名或直接登录");
            }
            throw new AuthProblem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "该邮箱已注册，请直接登录");
        }
        return UserVO.from(user);
    }

    @Transactional
    public GardenPrincipal loginAccount(String account, String password, String ip) {
        String identity = AccountIdentifier.normalize(account);
        limiter.check("login", identity, ip);
        boolean emailLogin = identity.contains("@");
        var user = emailLogin ? users.lockByEmail(identity) : users.lockByUsername(identity);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !"ACTIVE".equals(user.getStatus())) {
            throw new AuthProblem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    emailLogin ? "邮箱或密码错误" : "用户名或密码错误");
        }
        var now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user.setLastLoginAt(now);
        user.setUpdatedAt(now);
        users.updateById(user);
        return new GardenPrincipal(user.getId(), user.getCredentialVersion());
    }

    @Transactional(noRollbackFor = AuthProblem.class)
    public void resetPassword(ResetPasswordDTO request, String ip) {
        String email = AccountIdentifier.normalize(request.email());
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
