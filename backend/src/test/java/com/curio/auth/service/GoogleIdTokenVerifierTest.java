package com.curio.auth.service;

import com.curio.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "my-client-id.apps.googleusercontent.com";

    @Mock private RestTemplateBuilder restTemplateBuilder;
    @Mock private RestTemplate restTemplate;

    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        verifier = new GoogleIdTokenVerifier(restTemplateBuilder);
        verifier.init();
        configureClientId(CLIENT_ID);
    }

    /** Stands in for the @Value injection a plain Mockito unit test doesn't get. */
    private void configureClientId(String clientId) {
        ReflectionTestUtils.setField(verifier, "googleClientId", clientId);
    }

    private static Map<String, Object> tokenInfo(String aud, String iss) {
        Map<String, Object> info = new HashMap<>();
        info.put("sub", "google-sub-123");
        info.put("email", "g@example.com");
        info.put("name", "Google User");
        info.put("email_verified", "true");
        info.put("aud", aud);
        info.put("iss", iss);
        return info;
    }

    private void googleReturns(Map<String, Object> info) {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(info);
    }

    @Test
    void verify_returnsIdentity_whenAudienceAndIssuerMatch() {
        googleReturns(tokenInfo(CLIENT_ID, "https://accounts.google.com"));

        GoogleIdentity identity = verifier.verify("id-token");

        assertThat(identity.sub()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("g@example.com");
        assertThat(identity.name()).isEqualTo("Google User");
        assertThat(identity.emailVerified()).isTrue();
        // The raw token goes to Google's tokeninfo endpoint as the id_token query parameter.
        verify(restTemplate).getForObject(contains("oauth2.googleapis.com/tokeninfo?id_token=id-token"), eq(Map.class));
    }

    @Test
    void verify_acceptsBareIssuer() {
        googleReturns(tokenInfo(CLIENT_ID, "accounts.google.com"));

        assertThat(verifier.verify("id-token").sub()).isEqualTo("google-sub-123");
    }

    @Test
    void verify_reportsUnverifiedEmail_withoutRejecting() {
        // Whether an unverified email is acceptable is account policy, decided
        // by the caller; the verifier only reports Google's claim faithfully.
        Map<String, Object> info = tokenInfo(CLIENT_ID, "https://accounts.google.com");
        info.put("email_verified", "false");
        googleReturns(info);

        assertThat(verifier.verify("id-token").emailVerified()).isFalse();
    }

    @Test
    void verify_tolerates_missingName() {
        Map<String, Object> info = tokenInfo(CLIENT_ID, "https://accounts.google.com");
        info.remove("name");
        googleReturns(info);

        assertThat(verifier.verify("id-token").name()).isNull();
    }

    @Test
    void verify_rejects_whenAudienceMismatch() {
        googleReturns(tokenInfo("attacker-client-id", "https://accounts.google.com"));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google token");
    }

    @Test
    void verify_rejects_whenIssuerInvalid() {
        googleReturns(tokenInfo(CLIENT_ID, "https://evil.example.com"));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google token");
    }

    @Test
    void verify_rejects_whenClientIdNotConfigured() {
        // Fails closed: with no client id there is nothing to bind the token to,
        // so even a token that names "anything" as its audience must be refused.
        configureClientId("not-configured");
        googleReturns(tokenInfo("anything", "https://accounts.google.com"));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google sign-in is not enabled");
    }

    @Test
    void verify_rejects_whenClientIdBlank() {
        configureClientId("");
        googleReturns(tokenInfo("", "https://accounts.google.com"));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Google sign-in is not enabled");
    }

    @Test
    void verify_rejects_whenSubjectOrEmailMissing() {
        Map<String, Object> info = tokenInfo(CLIENT_ID, "https://accounts.google.com");
        info.remove("email");
        googleReturns(info);

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google token");
    }

    @Test
    void verify_rejects_emptyTokenInfo() {
        googleReturns(new HashMap<>());

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google token");
    }

    @Test
    void verify_rejects_whenGoogleIsUnreachable() {
        // A transport failure is reported as an invalid token, never as a 500,
        // and never leaks the underlying exception to the client.
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> verifier.verify("id-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid Google token");
    }
}
