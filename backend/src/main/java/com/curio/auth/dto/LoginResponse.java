package com.curio.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of the first step of email/password login.
 *
 * <ul>
 *   <li>{@code VERIFICATION_REQUIRED} — credentials were valid and a one-time
 *       code has been emailed. The client must call {@code /auth/verify-code}
 *       with {@code challengeId} and the code. No session is issued yet.</li>
 *   <li>{@code AUTHENTICATED} — email verification is disabled, so {@code auth}
 *       carries the issued session directly (backward-compatible path).</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String status;

    /** Opaque id the client echoes back to /auth/verify-code. Null when AUTHENTICATED. */
    private String challengeId;

    /** Email the code was sent to, for display ("we sent a code to …"). */
    private String email;

    /** Attempts left to enter the code correctly (starts at the configured max). */
    private Integer attemptsRemaining;

    /** Seconds until the code expires. */
    private Long expiresInSeconds;

    /** Populated only when status == AUTHENTICATED (verification disabled). */
    private AuthResponse auth;
}
