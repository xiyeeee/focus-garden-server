package com.xiye.garden.common;

import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestAccessLogFilterTests {
    private final RequestAccessLogFilter filter = new RequestAccessLogFilter();

    @Test
    void logsStatusAndRequestIdWithoutCredentials(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/email-codes");
        request.setQueryString("verificationCode=secret-query-code");
        request.setContent("{\"password\":\"secret-body-password\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Cookie", "GARDEN_SESSION=secret-session-cookie");
        request.addHeader("Authorization", "Bearer secret-authorization");
        request.addHeader("X-Request-ID", "untrusted-id");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> response.setStatus(429));
        assertTrue(output.getAll().contains("[API] -> POST /api/auth/email-codes"));
        assertTrue(output.getAll().contains("status=429"));
        assertTrue(output.getAll().contains("durationMs="));
        assertNotNull(response.getHeader("X-Request-ID"));
        assertNotEquals("untrusted-id", response.getHeader("X-Request-ID"));
        for (String secret : new String[]{"secret-query-code", "secret-body-password", "secret-session-cookie", "secret-authorization"}) {
            assertFalse(output.getAll().contains(secret), "Access logs must exclude request credentials");
        }
    }

    @Test
    void logsUnhandledFailureAs500WithoutExceptionContents(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/api/tasks");
        var response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> filter.doFilter(request, response, (incoming, outgoing) -> {
            throw new ServletException("secret-exception-content");
        }));
        assertTrue(output.getAll().contains("status=500"));
        assertFalse(output.getAll().contains("secret-exception-content"));
    }

    @Test
    void ignoresNonApiRequests(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/favicon.ico");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> response.setStatus(204));
        assertEquals(204, response.getStatus());
        assertNull(response.getHeader("X-Request-ID"));
        assertFalse(output.getAll().contains("[API]"));
    }
}
