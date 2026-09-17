package com.curio.auth.service;

/**
 * The claims Curio relies on from a verified Google ID token.
 *
 * @param sub           Google's stable account id; the value stored as {@code User.googleId}
 * @param email         the account email exactly as Google reports it (not yet normalized)
 * @param name          display name, may be null
 * @param emailVerified whether Google has confirmed the user owns {@code email}
 */
public record GoogleIdentity(String sub, String email, String name, boolean emailVerified) {
}
