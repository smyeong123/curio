package com.curio.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of submitting a login verification code.
 *
 * <ul>
 *   <li>{@code VERIFIED} — code correct; {@code auth} carries the session.</li>
 *   <li>{@code INVALID_CODE} — wrong code; {@code attemptsRemaining} shows how many tries are left.</li>
 *   <li>{@code LOCKED} — out of attempts; {@code resetAvailable} is true so the
 *       client can offer to send a password-reset email.</li>
 *   <li>{@code EXPIRED} — the challenge expired or is unknown; sign in again.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCodeResponse {

    private String status;

    private Integer attemptsRemaining;

    private String message;

    /** True when the user should be offered a password reset (status LOCKED). */
    private boolean resetAvailable;

    /** Populated only when status == VERIFIED. */
    private AuthResponse auth;
}
