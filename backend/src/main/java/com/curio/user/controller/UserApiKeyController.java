package com.curio.user.controller;

import com.curio.shared.security.InMemoryRateLimiter;
import com.curio.user.dto.ApiKeySummary;
import com.curio.user.dto.DeleteApiKeyRequest;
import com.curio.user.dto.SaveApiKeyRequest;
import com.curio.user.entity.User;
import com.curio.user.entity.UserApiKey;
import com.curio.user.port.in.UserApiKeyUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BYOK endpoints. All write operations require re-authenticating with the
 * current password (defense-in-depth: stolen JWT alone can't pivot to
 * draining someone's Anthropic credit).
 *
 * The re-auth path is itself rate-limited per user, so a stolen JWT can't be
 * used to brute-force the account password through these endpoints (the IP-based
 * {@code RateLimitingFilter} only covers {@code /api/v1/auth/}).
 *
 * GET returns metadata only — never the encrypted bytes or the plaintext key.
 */
@RestController
@RequestMapping("/api/v1/user/api-keys")
@RequiredArgsConstructor
@Tag(name = "Bring Your Own Key", description = "User-managed LLM API keys")
public class UserApiKeyController {

    private final UserApiKeyUseCase service;
    private final InMemoryRateLimiter rateLimiter;

    /** Password re-auth (save/delete): 5 attempts / 5 min per user. Throttles brute force. */
    private static final int REAUTH_CAPACITY = 5;
    private static final Duration REAUTH_REFILL = Duration.ofMinutes(5);

    /** Live provider validation: 5 calls / min per user. Cheap to provider, costly if abused. */
    private static final int VALIDATE_CAPACITY = 5;
    private static final Duration VALIDATE_REFILL = Duration.ofMinutes(1);

    private static final Map<String, String> TOO_MANY_REAUTH =
            Map.of("message", "Too many attempts — wait a few minutes and try again");
    private static final Map<String, String> TOO_MANY_VALIDATE =
            Map.of("message", "Too many validation attempts — wait a minute");

    @GetMapping
    @Operation(summary = "List the calling user's stored API keys (metadata only)")
    public ResponseEntity<List<ApiKeySummary>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.listForUser(user.getId()));
    }

    @PostMapping
    @Operation(summary = "Add or replace an API key for a provider")
    public ResponseEntity<?> save(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody SaveApiKeyRequest req) {
        if (!allowReauth(user.getId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(TOO_MANY_REAUTH);
        }
        ApiKeySummary saved = service.saveKey(
                user.getId(), req.getProvider(), req.getApiKey(),
                req.getCurrentPassword());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{provider}")
    @Operation(summary = "Delete the user's API key for a provider")
    public ResponseEntity<?> delete(@AuthenticationPrincipal User user,
                                    @PathVariable("provider") UserApiKey.Provider provider,
                                    @Valid @RequestBody DeleteApiKeyRequest req) {
        if (!allowReauth(user.getId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(TOO_MANY_REAUTH);
        }
        service.deleteKey(user.getId(), provider, req.getCurrentPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a key without saving it (returns true/false)")
    public ResponseEntity<?> validate(@AuthenticationPrincipal User user,
                                      @Valid @RequestBody ValidateRequest req) {
        if (!rateLimiter.tryConsume("byok-validate:" + user.getId(),
                VALIDATE_CAPACITY, VALIDATE_REFILL)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(TOO_MANY_VALIDATE);
        }
        boolean ok = service.validateKey(req.getProvider(), req.getApiKey());
        return ResponseEntity.ok(Map.of("valid", ok));
    }

    /** Rate-limit the password re-auth so a stolen JWT can't brute-force the password here. */
    private boolean allowReauth(UUID userId) {
        return rateLimiter.tryConsume("byok-reauth:" + userId, REAUTH_CAPACITY, REAUTH_REFILL);
    }

    @Data
    public static class ValidateRequest {
        @NotNull
        private UserApiKey.Provider provider;
        @NotBlank
        @ToString.Exclude   // never let the plaintext key reach a log / toString dump
        private String apiKey;
    }
}
