package com.curio.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtils {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String COOKIE_PATH = "/api/v1/auth";

    @Value("${app.cookie.secure:true}")
    private boolean secure;

    // Fallback matches the real session length (3h sliding idle timeout,
    // JWT_REFRESH_EXPIRATION) — the property is always present, but a stale 7-day
    // fallback here would hand out a cookie that long outlives its token.
    @Value("${jwt.refresh-expiration:10800000}")
    private long refreshTokenExpirationMs;

    public ResponseCookie createRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH)
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    public static String getCookieName() {
        return REFRESH_TOKEN_COOKIE;
    }
}
