package com.curio.auth.dto;

/**
 * The one answer {@code POST /auth/register} gives, whatever the address's state
 * (new, already confirmed, or still unconfirmed), so the response can't be used
 * to tell which emails have accounts. No session is issued: the account only
 * becomes usable once the emailed confirmation link is opened.
 */
public record RegisterResponse(String status) {

    public static final String CONFIRMATION_SENT = "confirmation_sent";

    public static RegisterResponse confirmationSent() {
        return new RegisterResponse(CONFIRMATION_SENT);
    }
}
