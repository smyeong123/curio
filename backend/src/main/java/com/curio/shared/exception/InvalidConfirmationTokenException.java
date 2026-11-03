package com.curio.shared.exception;

/**
 * A sign-up confirmation link that is unknown, expired or already used. Mapped
 * to 400 + code {@code CONFIRMATION_TOKEN_INVALID}; the three cases share one
 * answer on purpose (the fix is the same: ask for a fresh link).
 */
public class InvalidConfirmationTokenException extends RuntimeException {

    public static final String CODE = "CONFIRMATION_TOKEN_INVALID";

    public InvalidConfirmationTokenException() {
        super("This confirmation link is invalid or has expired.");
    }
}
