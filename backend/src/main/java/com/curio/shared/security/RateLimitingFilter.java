package com.curio.shared.security;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_CAPACITY = 10;
    private static final Duration AUTH_REFILL = Duration.ofMinutes(1);

    // Stricter buckets for brute-force-prone endpoints.
    private static final int LOGIN_CAPACITY = 5;
    private static final Duration LOGIN_REFILL = Duration.ofMinutes(1);
    private static final int RESET_CAPACITY = 3;
    private static final Duration RESET_REFILL = Duration.ofMinutes(10);

    // Change-password re-auth: a stolen access token must not be able to brute-force
    // the current password (change-password → account takeover). Same threat the BYOK
    // re-auth endpoints were hardened against.
    private static final String PASSWORD_CHANGE_PATH = "/api/v1/user/me/password";
    private static final int PASSWORD_CHANGE_CAPACITY = 5;
    private static final Duration PASSWORD_CHANGE_REFILL = Duration.ofMinutes(5);

    // Buckets live in the shared in-process store, keyed "scope:clientIp" (see
    // InMemoryRateLimiter for eviction and the single-replica caveat).
    private final InMemoryRateLimiter limiter;

    // Number of trusted reverse-proxy hops in front of this app. The client IP is
    // the X-Forwarded-For element this many positions from the RIGHT (each trusted
    // proxy appends the peer it saw). Must be deployment-configurable: the live
    // nginx-edge is 1 hop; a Caddy→nginx chain is 2. Keying on the left-most XFF
    // element (client-controlled) let an attacker spoof a fresh bucket per request.
    @Value("${app.rate-limit.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    public RateLimitingFilter(InMemoryRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/auth/") && !path.equals(PASSWORD_CHANGE_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        BucketSpec spec = specFor(path);
        String key = spec.scope() + ":" + clientKey(request);
        ConsumptionProbe probe = limiter.probe(key, spec.capacity(), spec.refill());

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.addHeader("Retry-After", String.valueOf(Math.max(1, waitSeconds)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many auth requests. Try again shortly.\"}");
    }

    private BucketSpec specFor(String path) {
        if (path.equals(PASSWORD_CHANGE_PATH)) {
            return new BucketSpec("password-change", PASSWORD_CHANGE_CAPACITY, PASSWORD_CHANGE_REFILL);
        }
        if (path.contains("/login") || path.contains("/google")) {
            return new BucketSpec("login", LOGIN_CAPACITY, LOGIN_REFILL);
        }
        // The 2FA code step: /verify-code guesses are already bounded by the
        // per-challenge 5-attempt budget, but /resend-code triggers a real email
        // per call — keep both on the strict login-sized bucket rather than the
        // looser default so an IP can't spam a victim's inbox with codes.
        if (path.contains("/verify-code") || path.contains("/resend-code")) {
            return new BucketSpec("verify", LOGIN_CAPACITY, LOGIN_REFILL);
        }
        if (path.contains("/forgot-password") || path.contains("/reset-password")
                || path.contains("/password-reset") || path.contains("/confirm-reset")) {
            return new BucketSpec("reset", RESET_CAPACITY, RESET_REFILL);
        }
        return new BucketSpec("auth", AUTH_CAPACITY, AUTH_REFILL);
    }

    private record BucketSpec(String scope, int capacity, Duration refill) {}

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && trustedProxyHops > 0) {
            String[] parts = forwarded.split(",");
            // Trust only the entry `trustedProxyHops` positions from the RIGHT — the IP
            // the outermost trusted proxy actually observed. Anything to its left is
            // client-supplied and spoofable. Fall back to the direct peer if the header
            // has fewer entries than expected (i.e. it wasn't set by our proxy chain).
            int index = parts.length - trustedProxyHops;
            if (index >= 0 && index < parts.length) {
                String candidate = parts[index].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
