package com.curio.user.dto;

import lombok.Data;
import lombok.ToString;

/**
 * Proof of identity for account deletion. Password accounts send
 * {@code currentPassword}; Google-only accounts send a fresh
 * {@code googleIdToken}. Which one is required is decided server-side.
 */
@Data
public class DeleteAccountRequest {

    @ToString.Exclude   // never let the password reach a log / toString dump
    private String currentPassword;

    @ToString.Exclude   // a live Google ID token is a login credential
    private String googleIdToken;
}
