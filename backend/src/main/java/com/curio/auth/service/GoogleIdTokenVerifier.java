package com.curio.auth.service;

import com.curio.shared.exception.UnauthorizedException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Resolves a Google ID token to the {@link GoogleIdentity} it asserts.
 *
 * <p>Google's tokeninfo endpoint validates the signature and expiry but does
 * NOT bind the token to this application, so this class adds the audience and
 * issuer checks. Without them a token minted for any other Google OAuth client
 * would be accepted (token-audience confusion, an account-takeover vector).
 * Fails closed when no client id is configured.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleIdTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestTemplateBuilder restTemplateBuilder;

    /** The OAuth client id this app is registered under; the token's {@code aud} must match it. */
    @Value("${app.google-client-id:}")
    private String googleClientId;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * @throws UnauthorizedException when Google rejects the token, it was not
     *         issued by Google for this application, or it carries no usable
     *         subject/email.
     */
    public GoogleIdentity verify(String idToken) {
        Map<String, Object> claims = fetchTokenInfo(idToken);
        validateAudienceAndIssuer(claims);

        String sub = asString(claims.get("sub"));
        String email = asString(claims.get("email"));
        if (sub == null || email == null) {
            throw new UnauthorizedException("Invalid Google token");
        }
        boolean emailVerified = Boolean.parseBoolean(asString(claims.get("email_verified")));
        return new GoogleIdentity(sub, email, asString(claims.get("name")), emailVerified);
    }

    private Map<String, Object> fetchTokenInfo(String idToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(TOKENINFO_URL)
                .queryParam("id_token", idToken)
                .toUriString();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || response.isEmpty()) {
                throw new UnauthorizedException("Invalid Google token");
            }
            return response;
        } catch (RestClientException ex) {
            log.warn("Google token verification failed", ex);
            throw new UnauthorizedException("Invalid Google token");
        }
    }

    private void validateAudienceAndIssuer(Map<String, Object> claims) {
        if (googleClientId == null || googleClientId.isBlank()
                || "not-configured".equals(googleClientId)) {
            log.warn("Google login attempted but GOOGLE_CLIENT_ID is not configured");
            throw new UnauthorizedException("Google sign-in is not enabled");
        }
        String aud = asString(claims.get("aud"));
        if (!googleClientId.equals(aud)) {
            log.warn("Rejected Google token with mismatched audience: {}", aud);
            throw new UnauthorizedException("Invalid Google token");
        }
        String iss = asString(claims.get("iss"));
        if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
            log.warn("Rejected Google token with invalid issuer: {}", iss);
            throw new UnauthorizedException("Invalid Google token");
        }
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
