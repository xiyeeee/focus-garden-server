package com.xiye.garden.auth.security;

import com.xiye.garden.user.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class SessionUserFilter extends OncePerRequestFilter {
    private final UserMapper users;
    private final SecurityJsonResponse responses;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof GardenPrincipal principal) {
            com.xiye.garden.user.entity.UserEntity user;
            try {
                user = users.selectById(principal.userId());
            } catch (org.springframework.dao.DataAccessException exception) {
                responses.error(response, 503, "SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试");
                return;
            }
            // Version checks also reject sessions saved by an in-flight request after a password reset.
            if (user == null || !"ACTIVE".equals(user.getStatus())
                    || user.getCredentialVersion() != principal.credentialVersion()) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                var cookie = new Cookie("GARDEN_SESSION", "");
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setSecure(request.isSecure());
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }
        }
        chain.doFilter(request, response);
    }
}
