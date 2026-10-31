package com.curio.auth.port.in;

/**
 * Re-authentication for accounts that have no password (Google-only sign-in):
 * a destructive action asks the browser for a fresh Google ID token and the
 * server checks it belongs to the same Google account. A stolen access token
 * alone cannot produce one.
 */
public interface GoogleReauthUseCase {

    /**
     * @return true only when {@code idToken} is a valid Google ID token issued to
     *         this app for the Google account {@code expectedGoogleSub}.
     */
    boolean confirmsAccount(String idToken, String expectedGoogleSub);
}
