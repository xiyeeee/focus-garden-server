package com.xiye.garden.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestAccessLogFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
        String method = request.getMethod();
        long start = System.nanoTime();
        boolean completed = false;
        response.setHeader("X-Request-ID", requestId);
        log.info("[API] -> {} {} requestId={}", method, path, requestId);
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            log.info("[API] <- {} {} status={} durationMs={} requestId={}", method, path, status, durationMs, requestId);
        }
    }
}
