package com.curio.user.port.in;

import com.curio.user.entity.UserApiKey;

/**
 * Port the user-facing service uses to validate a freshly-submitted key
 * against the LLM provider. Implementations live in the {@code news}
 * package alongside the real client wiring (so they share RestTemplate
 * config) and are invoked here over a port to keep hexagonal boundaries.
 */
public interface ApiKeyValidator {

    /** True iff the provider returns a 2xx for a tiny credentials check. */
    boolean isValid(UserApiKey.Provider provider, String rawApiKey);
}
