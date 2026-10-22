package com.travelalbum.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giới hạn số request tới /api/share/** (public, chỉ cần token) theo IP để chặn dò đoán
 * share token. Dùng request.getRemoteAddr() — KHÔNG đọc thẳng header X-Forwarded-For vì
 * client tự đặt được; Tomcat (forward-headers-strategy: native) chỉ thay IP thật từ các
 * proxy tin cậy. Bộ đếm in-memory, đủ cho 1 instance (nhiều instance cần Redis).
 */
@Component
public class ShareRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS_PER_MINUTE = 60;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(100_000)
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/share/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int count = counters.get(request.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet();
        if (count > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"success\":false,\"message\":\"Too many requests\",\"data\":null,\"errorCode\":\"TOO_MANY_REQUESTS\"}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
