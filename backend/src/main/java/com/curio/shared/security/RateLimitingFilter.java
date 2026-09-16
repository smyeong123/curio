package com.curio.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

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

    // Buckets are held in-process keyed by client IP. Without eviction this map
    // would grow once per distinct attacker/client IP and never shrink — an
    // unbounded leak on a long-running instance. A periodic sweep drops buckets
    // that have been idle longer than IDLE_TTL (any survivor would have fully
    // refilled by then anyway, so dropping it is behaviourally equivalent to
    // keeping it). NOTE: this is per-instance state; running more than one
    // backend replica needs a shared store (e.g. Redis-backed Bucket4j).
    private static final Duration IDLE_TTL = Duration.ofMinutes(15);

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    // Number of trusted reverse-proxy hops in front of this app. The client IP is
    // the X-Forwarded-For element this many positions from the RIGHT (each trusted
    // proxy appends the peer it saw). Must be deployment-configurable: the live
    // nginx-edge is 1 hop; a Caddy→nginx chain is 2. Keying on the left-most XFF
    // element (client-controlled) let an attacker spoof a fresh bucket per request.
    @Value("${app.rate-limit.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    private static final class BucketEntry {
        final Bucket bucket;
        volatile long lastAccessNanos;
        BucketEntry(Bucket bucket, long nowNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = nowNanos;
        }
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
        BucketEntry entry = buckets.computeIfAbsent(key, k -> new BucketEntry(newBucket(spec), System.nanoTime()));
        entry.lastAccessNanos = System.nanoTime();
        Bucket bucket = entry.bucket;
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

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

    /**
     * Drop idle buckets so the map can't grow without bound. Runs every 5
     * minutes; a bucket untouched for {@link #IDLE_TTL} is removed (it would be
     * fully refilled by now, so a returning client just gets a fresh bucket).
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    void evictIdleBuckets() {
        long cutoff = System.nanoTime() - IDLE_TTL.toNanos();
        int before = buckets.size();
        buckets.values().removeIf(e -> e.lastAccessNanos < cutoff);
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("Rate-limit bucket eviction: removed {} idle buckets, {} remain", removed, buckets.size());
        }
    }

    private Bucket newBucket(BucketSpec spec) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(spec.capacity())
                .refillGreedy(spec.capacity(), spec.refill())
                .build();
        return Bucket.builder().addLimit(limit).build();
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
