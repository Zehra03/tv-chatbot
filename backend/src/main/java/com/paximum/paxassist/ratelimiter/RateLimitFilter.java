package com.paximum.paxassist.ratelimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sabit pencereli (fixed-window) istek sınırlayıcı: istemci IP'si başına
 * {@code app.rate-limit.window-seconds} içinde en fazla {@code app.rate-limit.max-requests}
 * isteğe izin verir. Pipeline'daki yeri gereği tüm filtrelerden önce çalışır
 * (ratelimiter -> guard -> orchestrator, bkz. docs/architecture.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int PRUNE_THRESHOLD = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.max-requests}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        pruneIfNeeded(now);

        Window window = windows.compute(clientKey(request), (key, existing) ->
                (existing == null || now - existing.startMillis >= windowMillis)
                        ? new Window(now)
                        : existing);

        if (window.count.incrementAndGet() > maxRequests) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"RATE_LIMITED\","
                    + "\"message\":\"İstek limiti aşıldı, lütfen bekleyin\","
                    + "\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Süresi dolmuş pencereleri temizleyerek map'in sınırsız büyümesini engeller
    private void pruneIfNeeded(long now) {
        if (windows.size() > PRUNE_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis >= windowMillis);
        }
    }

    private static final class Window {
        final long startMillis;
        final AtomicInteger count = new AtomicInteger();

        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
