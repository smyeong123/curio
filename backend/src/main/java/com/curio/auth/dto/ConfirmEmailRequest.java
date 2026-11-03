package com.curio.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The raw token from the sign-up confirmation link ({@code /verify-email?token=...}). */
public record ConfirmEmailRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 255, message = "Token is too long")
        String token) {
}
