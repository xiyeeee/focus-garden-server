package com.xiye.garden.auth.security;

import com.xiye.garden.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class SecurityJsonResponse {
    private final JsonMapper json;

    public void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }

    public void denied(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response,
                       org.springframework.security.access.AccessDeniedException exception) throws IOException {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = authentication != null && authentication.getPrincipal() instanceof GardenPrincipal;
        boolean publicAuth = java.util.Set.of("/api/auth/login", "/api/auth/register", "/api/auth/email-codes",
                "/api/auth/password/reset", "/api/auth/logout").contains(request.getServletPath());
        if (!loggedIn && !publicAuth) {
            error(response, 401, "UNAUTHORIZED", "请先登录或重新登录");
            return;
        }
        boolean csrf = exception instanceof org.springframework.security.web.csrf.CsrfException;
        error(response, 403, csrf ? "CSRF_INVALID" : "FORBIDDEN",
                csrf ? "安全令牌无效，请刷新后重试" : "无权访问此资源");
    }

    public void logout(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.success(null));
    }
}
