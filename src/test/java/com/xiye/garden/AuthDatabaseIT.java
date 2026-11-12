package com.xiye.garden;

import com.xiye.garden.auth.entity.VerificationPurpose;
import com.xiye.garden.auth.service.VerificationProtector;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class AuthDatabaseIT {
    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private VerificationProtector protector;
    @MockitoBean
    private JavaMailSender mail;
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private static final String PASSWORD = " 安全密码🌱123 ";

    @BeforeEach
    void captureTestMail() {
        // Reset only this test client's IP buckets in the independent integration database.
        for (String operation : List.of("send", "verify", "login", "register")) {
            jdbc.update("DELETE FROM garden_auth_rate_limit WHERE bucket_key=?", protector.digest("rate:" + operation + ":ip:127.0.0.1"));
        }
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            var matcher = Pattern.compile("验证码：([0-9]{6})").matcher(Objects.requireNonNull(message.getText()));
            assertTrue(matcher.find());
            String purpose = Objects.requireNonNull(message.getSubject()).contains("注册") ? "REGISTER" : "RESET_PASSWORD";
            received.put(Objects.requireNonNull(message.getTo())[0] + ":" + purpose, matcher.group(1));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void registrationNormalizationDuplicateAndOneTimeCode() throws Exception {
        String email = email();
        Browser browser = new Browser();
        browser.refreshCsrf();
        String code = sendCode(browser, email, "REGISTER");
        var response = browser.call("POST", "/api/auth/register", registration(" " + email.toUpperCase(Locale.ROOT) + " ", code));
        assertEquals(201, response.statusCode());
        var user = body(response).path("data");
        assertEquals(email, user.path("email").asText());
        assertTrue(user.path("id").isString());
        assertFalse(user.has("passwordHash"));
        assertEquals(401, browser.call("GET", "/api/users/me", null).statusCode());
        assertEquals(400, browser.call("POST", "/api/auth/register", registration(email, code)).statusCode());
        allowResend(email, "REGISTER");
        String secondCode = sendCode(browser, email, "REGISTER");
        assertEquals(409, browser.call("POST", "/api/auth/register", registration(email, secondCode)).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM garden_user WHERE email=?", Integer.class, email));
    }

    @Test
    void wrongExpiredPurposeAndMaximumFailedAttempts() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String email = email();
        String code = sendCode(browser, email, "REGISTER");
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(400, browser.call("POST", "/api/auth/register", registration(email, wrong)).statusCode());
        }
        assertEquals(5, jdbc.queryForObject("SELECT failed_attempts FROM garden_email_verification WHERE email=? AND purpose='REGISTER'", Integer.class, email));
        assertEquals(400, browser.call("POST", "/api/auth/register", registration(email, code)).statusCode());
        String expiredEmail = email();
        String expiredCode = sendCode(browser, expiredEmail, "REGISTER");
        jdbc.update("UPDATE garden_email_verification SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE email=?", expiredEmail);
        assertEquals(400, browser.call("POST", "/api/auth/register", registration(expiredEmail, expiredCode)).statusCode());
        String purposeEmail = email();
        String resetCode = sendCode(browser, purposeEmail, "RESET_PASSWORD");
        assertEquals(400, browser.call("POST", "/api/auth/register", registration(purposeEmail, resetCode)).statusCode());
    }

    @Test
    void concurrentRegistrationConsumesCodeOnce() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String email = email();
        String code = sendCode(browser, email, "REGISTER");
        var attempts = List.of(CompletableFuture.supplyAsync(() -> registerStatus(browser, email, code)),
                CompletableFuture.supplyAsync(() -> registerStatus(browser, email, code)));
        var results = attempts.stream().map(CompletableFuture::join).sorted().toList();
        assertEquals(List.of(201, 400), results);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM garden_user WHERE email=?", Integer.class, email));
    }

    @Test
    void loginSessionRotationCookiePersistenceCsrfAndLogout() throws Exception {
        String email = registered();
        Browser browser = new Browser();
        browser.refreshCsrf();
        String before = browser.sessionCookie();
        var login = browser.call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(200, login.statusCode());
        assertTrue(!before.equals(browser.sessionCookie()), "登录应轮换会话");
        assertTrue(login.headers().allValues("set-cookie").stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("httponly")));
        String userId = body(login).path("data").path("id").asText();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", Integer.class, userId));
        assertEquals(1800, jdbc.queryForObject("SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", Integer.class, userId));
        assertEquals(200, browser.call("GET", "/api/users/me", null).statusCode());
        // Login clears the old CSRF token; a stale token must fail.
        assertEquals(403, browser.call("POST", "/api/tasks", task()).statusCode());
        browser.refreshCsrf();
        String valid = browser.csrf;
        browser.csrf = null;
        assertEquals(403, browser.call("POST", "/api/tasks", task()).statusCode());
        browser.csrf = valid;
        assertEquals(201, browser.call("POST", "/api/tasks", task()).statusCode());
        assertEquals(200, browser.call("POST", "/api/auth/logout", null).statusCode());
        assertEquals(401, browser.call("GET", "/api/users/me", null).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", Integer.class, userId));
    }

    @Test
    void anonymousAndDisabledUserCannotAuthenticate() throws Exception {
        Browser browser = new Browser();
        assertEquals(401, browser.call("GET", "/api/tasks", null).statusCode());
        assertEquals(401, browser.call("POST", "/api/tasks", task()).statusCode());
        assertEquals(403, browser.call("POST", "/api/auth/login", Map.of("email", email(), "password", PASSWORD)).statusCode());
        String email = registered();
        browser.refreshCsrf();
        assertEquals(401, browser.call("POST", "/api/auth/login", Map.of("email", email, "password", "wrongpass")).statusCode());
        jdbc.update("UPDATE garden_user SET status='DISABLED' WHERE email=?", email);
        var response = browser.call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(401, response.statusCode());
        assertEquals("邮箱或密码错误", body(response).path("message").asText());
    }

    @Test
    void resetChangesPasswordAndRevokesAllSessions() throws Exception {
        String email = registered();
        Browser first = login(email);
        Browser second = login(email);
        String userId = body(first.call("GET", "/api/users/me", null)).path("data").path("id").asText();
        String code = sendCode(first, email, "RESET_PASSWORD");
        String newPassword = " 新的密码🌱456 ";
        assertEquals(200, first.call("POST", "/api/auth/password/reset",
                Map.of("email", email, "verificationCode", code, "newPassword", newPassword)).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME=?", Integer.class, userId));
        assertEquals(401, second.call("GET", "/api/users/me", null).statusCode());
        first.refreshCsrf();
        assertEquals(401, first.call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD)).statusCode());
        assertEquals(200, first.call("POST", "/api/auth/login", Map.of("email", email, "password", newPassword)).statusCode());
        first.refreshCsrf();
        assertEquals(400, first.call("POST", "/api/auth/password/reset",
                Map.of("email", email, "verificationCode", code, "newPassword", PASSWORD)).statusCode());
    }

    @Test
    void tasksAreIsolatedIncludingLegacyRows() throws Exception {
        Browser owner = login(registered());
        var created = owner.call("POST", "/api/tasks", task());
        assertEquals(201, created.statusCode());
        String id = body(created).path("data").path("id").asText();
        Browser stranger = login(registered());
        assertEquals(404, stranger.call("GET", "/api/tasks/" + id, null).statusCode());
        assertEquals(404, stranger.call("PUT", "/api/tasks/" + id, task()).statusCode());
        assertEquals(404, stranger.call("DELETE", "/api/tasks/" + id, null).statusCode());
        assertEquals(404, stranger.call("POST", "/api/tasks/" + id + "/complete", null).statusCode());
        assertEquals(0, body(stranger.call("GET", "/api/tasks", null)).path("data").path("total").asInt());
        jdbc.update("INSERT INTO tasks(title,description,planned_focus_minutes,status,created_at,updated_at) VALUES ('隔离历史任务','',25,'TODO',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        Long legacyId = jdbc.queryForObject("SELECT MAX(id) FROM tasks WHERE user_id IS NULL", Long.class);
        assertEquals(404, owner.call("GET", "/api/tasks/" + legacyId, null).statusCode());
        assertEquals(200, owner.call("GET", "/api/tasks/" + id, null).statusCode());
        var spoofed = new HashMap<>(task());
        spoofed.put("userId", "1");
        assertEquals(400, stranger.call("POST", "/api/tasks", spoofed).statusCode());
    }

    @Test
    void sendAndLoginRateLimitsReturn429() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String email = email();
        sendCode(browser, email, "RESET_PASSWORD");
        var limited = browser.call("POST", "/api/auth/email-codes", Map.of("email", email, "purpose", "RESET_PASSWORD"));
        assertEquals(429, limited.statusCode());
        assertTrue(limited.headers().firstValue("X-Request-ID").isPresent());
        assertTrue(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow()) > 0);
        String missing = email();
        for (int count = 0; count < 10; count++) {
            assertEquals(401, browser.call("POST", "/api/auth/login", Map.of("email", missing, "password", PASSWORD)).statusCode());
        }
        assertEquals(429, browser.call("POST", "/api/auth/login", Map.of("email", missing, "password", PASSWORD)).statusCode());
    }

    @Test
    void failedMailRollsBackNewChallengeAndResendInvalidatesOldCode() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String email = email();
        String old = sendCode(browser, email, "REGISTER");
        String oldHash = jdbc.queryForObject("SELECT code_hash FROM garden_email_verification WHERE email=?", String.class, email);
        allowResend(email, "REGISTER");
        doThrow(new MailSendException("Test SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertEquals(503, browser.call("POST", "/api/auth/email-codes", Map.of("email", email, "purpose", "REGISTER")).statusCode());
        assertEquals(oldHash, jdbc.queryForObject("SELECT code_hash FROM garden_email_verification WHERE email=?", String.class, email));
        captureTestMail();
        allowResend(email, "REGISTER");
        String current = sendCode(browser, email, "REGISTER");
        assertNotEquals(old, current);
        assertEquals(400, browser.call("POST", "/api/auth/register", registration(email, old)).statusCode());
        assertEquals(201, browser.call("POST", "/api/auth/register", registration(email, current)).statusCode());
    }

    @Test
    void expiredAndDisabledSessionsReturn401() throws Exception {
        String email = registered();
        Browser browser = login(email);
        jdbc.update("UPDATE garden_user SET status='DISABLED' WHERE email=?", email);
        assertEquals(401, browser.call("GET", "/api/users/me", null).statusCode());
        String anotherEmail = registered();
        Browser another = login(anotherEmail);
        String id = body(another.call("GET", "/api/users/me", null)).path("data").path("id").asText();
        jdbc.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME=0, EXPIRY_TIME=0 WHERE PRINCIPAL_NAME=?", id);
        assertEquals(401, another.call("GET", "/api/users/me", null).statusCode());
    }

    @Test
    void ipLimitsAndVerificationLimitsAreEnforcedFromDatabase() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        seedRate("login:ip:127.0.0.1", 50, 900);
        assertEquals(429, browser.call("POST", "/api/auth/login", Map.of("email", email(), "password", PASSWORD)).statusCode());
        seedRate("send:ip:127.0.0.1", 20, 3600);
        assertEquals(429, browser.call("POST", "/api/auth/email-codes", Map.of("email", email(), "purpose", "REGISTER")).statusCode());
        seedRate("verify:ip:127.0.0.1", 30, 900);
        assertEquals(429, browser.call("POST", "/api/auth/register", registration(email(), "123456")).statusCode());
    }

    @Test
    void corsAllowsOnlyExplicitCredentialedOrigin() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token").build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
    }

    private void seedRate(String dimension, int attempts, int window) {
        long epoch = java.time.Instant.now().getEpochSecond();
        long start = epoch - epoch % window;
        jdbc.update("INSERT INTO garden_auth_rate_limit(bucket_key,window_start,attempts,expires_at) VALUES (?,?,?,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR) ON DUPLICATE KEY UPDATE attempts=VALUES(attempts)",
                protector.digest("rate:" + dimension), start, attempts);
    }

    @Test
    void usernameRegistrationAndLoginWorkWithoutEmailOrCode() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String username = username();
        var response = browser.call("POST", "/api/auth/register", Map.of("username", " " + username.toUpperCase(Locale.ROOT) + " ",
                "password", PASSWORD, "nickname", "用户名花友"));
        assertEquals(201, response.statusCode());
        var user = body(response).path("data");
        assertEquals(username, user.path("username").asText());
        assertTrue(user.path("email").isNull());
        assertEquals(401, browser.call("GET", "/api/users/me", null).statusCode());
        verify(mail, never()).send(any(SimpleMailMessage.class));
        String hash = jdbc.queryForObject("SELECT password_hash FROM garden_user WHERE username=?", String.class, username);
        assertTrue(hash != null && hash.startsWith("{pbkdf2@SpringSecurity_v5_8}"));
        assertEquals(200, browser.call("POST", "/api/auth/login", Map.of("username", username.toUpperCase(Locale.ROOT), "password", PASSWORD)).statusCode());
        browser.refreshCsrf();
        assertEquals(username, body(browser.call("GET", "/api/users/me", null)).path("data").path("username").asText());
        assertEquals(201, browser.call("POST", "/api/tasks", task()).statusCode());
        assertEquals(200, browser.call("POST", "/api/auth/logout", null).statusCode());
        assertEquals(401, browser.call("GET", "/api/users/me", null).statusCode());
    }

    @Test
    void usernameDuplicateConcurrentRegistrationAndLoginFailureAreHandled() throws Exception {
        String username = username();
        Browser first = new Browser();
        Browser second = new Browser();
        first.refreshCsrf();
        second.refreshCsrf();
        var payload = Map.<String, Object>of("username", username, "password", PASSWORD);
        var attempts = List.of(CompletableFuture.supplyAsync(() -> postRegistration(first, payload)),
                CompletableFuture.supplyAsync(() -> postRegistration(second, payload)));
        assertEquals(List.of(201, 409), attempts.stream().map(CompletableFuture::join).sorted().toList());
        var duplicate = first.call("POST", "/api/auth/register", Map.of("username", username.toUpperCase(Locale.ROOT), "password", PASSWORD));
        assertEquals(409, duplicate.statusCode());
        assertEquals("USERNAME_ALREADY_REGISTERED", body(duplicate).path("code").asText());
        var wrong = first.call("POST", "/api/auth/login", Map.of("username", username, "password", "wrongpass"));
        assertEquals(401, wrong.statusCode());
        assertEquals("用户名或密码错误", body(wrong).path("message").asText());
        jdbc.update("UPDATE garden_user SET status='DISABLED' WHERE username=?", username);
        assertEquals(401, first.call("POST", "/api/auth/login", payload).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM garden_user WHERE username=?", Integer.class, username));
    }

    @Test
    void registrationAndLoginRejectInvalidOrAmbiguousIdentity() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        for (String invalid : List.of("ab", "2bad", "bad-name", "花友名字", "a".repeat(33), "")) {
            assertEquals(400, browser.call("POST", "/api/auth/register", Map.of("username", invalid, "password", PASSWORD)).statusCode());
        }
        assertEquals(400, browser.call("POST", "/api/auth/register", Map.of("username", username(), "email", email(),
                "verificationCode", "123456", "password", PASSWORD)).statusCode());
        assertEquals(400, browser.call("POST", "/api/auth/register", Map.of("email", email(), "password", PASSWORD)).statusCode());
        assertEquals(400, browser.call("POST", "/api/auth/login", Map.of("email", email(), "username", username(), "password", PASSWORD)).statusCode());
        assertEquals(400, browser.call("POST", "/api/auth/login", Map.of("password", PASSWORD)).statusCode());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailAccountCanBindUsernameOnceWithDuplicateRejected() throws Exception {
        String email = registered();
        Browser browser = login(email);
        String username = username();
        var bind = browser.call("PUT", "/api/users/me/username", Map.of("username", " " + username.toUpperCase(Locale.ROOT) + " "));
        assertEquals(200, bind.statusCode());
        var user = body(bind).path("data");
        assertEquals(username, user.path("username").asText());
        assertEquals(email, user.path("email").asText());
        var rebound = browser.call("PUT", "/api/users/me/username", Map.of("username", username()));
        assertEquals(409, rebound.statusCode());
        assertEquals("USERNAME_ALREADY_BOUND", body(rebound).path("code").asText());

        assertEquals(200, browser.call("POST", "/api/auth/logout", null).statusCode());
        browser.refreshCsrf();
        assertEquals(200, browser.call("POST", "/api/auth/login", Map.of("username", username, "password", PASSWORD)).statusCode());
        browser.refreshCsrf();
        assertEquals(200, browser.call("POST", "/api/auth/logout", null).statusCode());
        browser.refreshCsrf();
        assertEquals(200, browser.call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD)).statusCode());

        String otherEmail = registered();
        Browser other = login(otherEmail);
        var duplicate = other.call("PUT", "/api/users/me/username", Map.of("username", username.toUpperCase(Locale.ROOT)));
        assertEquals(409, duplicate.statusCode());
        assertEquals("USERNAME_ALREADY_REGISTERED", body(duplicate).path("code").asText());
        assertTrue(body(duplicate).path("message").asText().contains("重复"));
        assertNull(jdbc.queryForObject("SELECT username FROM garden_user WHERE email=?", String.class, otherEmail));
    }

    @Test
    void usernameRegistrationAndLoginRateLimitsAreEnforced() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String username = username();
        var payload = Map.<String, Object>of("username", username, "password", PASSWORD);
        assertEquals(201, browser.call("POST", "/api/auth/register", payload).statusCode());
        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals(409, browser.call("POST", "/api/auth/register", payload).statusCode());
        }
        assertEquals(429, browser.call("POST", "/api/auth/register", payload).statusCode());
        String unknownUsername = username();
        for (int attempt = 0; attempt < 10; attempt++) {
            assertEquals(401, browser.call("POST", "/api/auth/login", Map.of("username", unknownUsername, "password", PASSWORD)).statusCode());
        }
        assertEquals(429, browser.call("POST", "/api/auth/login", Map.of("username", unknownUsername, "password", PASSWORD)).statusCode());
        seedRate("register:ip:127.0.0.1", 20, 3600);
        assertEquals(429, browser.call("POST", "/api/auth/register", Map.of("username", username(), "password", PASSWORD)).statusCode());
    }

    private String username() { return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }

    private int postRegistration(Browser browser, Map<String, Object> payload) {
        try { return browser.call("POST", "/api/auth/register", payload).statusCode(); }
        catch (Exception exception) { throw new IllegalStateException("并发注册请求失败", exception); }
    }

    private String registered() throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        String email = email();
        String code = sendCode(browser, email, "REGISTER");
        assertEquals(201, browser.call("POST", "/api/auth/register", registration(email, code)).statusCode());
        return email;
    }

    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        browser.refreshCsrf();
        assertEquals(200, browser.call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD)).statusCode());
        browser.refreshCsrf();
        return browser;
    }

    private String sendCode(Browser browser, String email, String purpose) throws Exception {
        assertEquals(200, browser.call("POST", "/api/auth/email-codes", Map.of("email", email, "purpose", purpose)).statusCode());
        return Objects.requireNonNull(received.get(email + ":" + purpose));
    }

    private void allowResend(String email, String purpose) {
        jdbc.update("UPDATE garden_email_verification SET created_at=UTC_TIMESTAMP(6)-INTERVAL 61 SECOND WHERE email=? AND purpose=?", email, purpose);
        jdbc.update("DELETE FROM garden_auth_rate_limit WHERE bucket_key=?", protector.digest("rate:send:" + purpose + ":cooldown:" + email));
    }

    private int registerStatus(Browser browser, String email, String code) {
        try { return browser.call("POST", "/api/auth/register", registration(email, code)).statusCode(); }
        catch (Exception exception) { throw new IllegalStateException("并发注册请求失败", exception); }
    }

    private Map<String, Object> registration(String email, String code) {
        return Map.of("email", email, "password", PASSWORD, "verificationCode", code, "nickname", "测试花友");
    }

    private Map<String, Object> task() {
        return Map.of("title", "用户隔离任务", "plannedFocusMinutes", 25, "status", "TODO");
    }

    private String email() { return UUID.randomUUID() + "@auth.test"; }
    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        private String csrf;

        HttpResponse<String> call(String method, String path, Map<String, Object> payload) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Accept", "application/json");
            if (csrf != null && !method.equals("GET")) builder.header("X-CSRF-TOKEN", csrf);
            if (payload != null) builder.header("Content-Type", "application/json");
            builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        void refreshCsrf() throws Exception {
            var response = call("GET", "/api/auth/csrf", null);
            assertEquals(200, response.statusCode());
            csrf = body(response).path("data").path("token").asText();
        }

        String sessionCookie() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("GARDEN_SESSION"))
                    .findFirst().orElseThrow().getValue();
        }
    }
}
