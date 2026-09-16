package com.curio.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of asking for a fresh login verification code.
 * {@code SENT} — a new code was emailed; {@code EXPIRED} — the challenge is gone, sign in again.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendCodeResponse {

    private String status;

    private Integer attemptsRemaining;

    private Long expiresInSeconds;

    private String message;
}
