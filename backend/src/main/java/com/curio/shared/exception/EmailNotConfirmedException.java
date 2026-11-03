package com.curio.shared.exception;

/**
 * A password login with the RIGHT password for an account whose sign-up email
 * was never confirmed. Mapped to 403 + code {@code EMAIL_NOT_CONFIRMED} so the
 * login screen can offer a fresh confirmation link. Thrown only after the
 * password checked out, so it reveals nothing to someone who doesn't know it.
 * (403, not 401: the axios interceptor only attempts a silent refresh on 401.)
 */
public class EmailNotConfirmedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_CONFIRMED";

    public EmailNotConfirmedException() {
        super("Please confirm your email address before signing in.");
    }
}
