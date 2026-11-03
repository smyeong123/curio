package com.curio.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ask for a fresh sign-up confirmation link. {@code language} ("en" | "ko",
 * optional) is the edition the browser is showing; the email is written in it.
 */
public record ResendConfirmationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        @Size(max = 8)
        String language) {
}
