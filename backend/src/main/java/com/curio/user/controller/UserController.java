package com.curio.user.controller;

import com.curio.shared.security.InMemoryRateLimiter;
import com.curio.user.dto.ChangePasswordRequest;
import com.curio.user.dto.DeleteAccountRequest;
import com.curio.user.dto.PreferencesRequest;
import com.curio.user.dto.PreferencesResponse;
import com.curio.user.dto.UpdateProfileRequest;
import com.curio.user.dto.UserResponse;
import com.curio.user.entity.User;
import com.curio.user.port.in.UserUseCase;
import com.curio.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile and preferences")
public class UserController {

    private final UserUseCase userService;
    /** Renders the server-side unsubscribe pages (templates/unsubscribe-*.html). */
    private final TemplateEngine templateEngine;
    private final InMemoryRateLimiter rateLimiter;

    /**
     * Password/Google re-auth on change-password and delete-account: 5 tries per
     * 5 min per USER. RateLimitingFilter's per-IP bucket alone lets a stolen
     * access token guess the password from many IPs (same reasoning as BYOK).
     */
    private static final int REAUTH_CAPACITY = 5;
    private static final Duration REAUTH_REFILL = Duration.ofMinutes(5);
    private static final Map<String, String> TOO_MANY_REAUTH =
            Map.of("message", "Too many attempts — wait a few minutes and try again");

    /** Where the pages' "Curio." wordmark links: the site, not this API origin. */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getProfile(user.getId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                userService.updateProfile(user.getId(), request.getFullName(), request.getDeliveryEnabled()));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get user topic preferences + delivery time")
    public ResponseEntity<PreferencesResponse> getPreferences(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getPreferencesDetail(user.getId()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update user topic preferences (and optional timezone/deliveryHour)")
    public ResponseEntity<PreferencesResponse> updatePreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PreferencesRequest request) {
        userService.updatePreferences(user.getId(), request);
        return ResponseEntity.ok(userService.getPreferencesDetail(user.getId()));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change user password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (!allowReauth(user.getId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(TOO_MANY_REAUTH);
        }
        userService.changePassword(user.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete user account")
    public ResponseEntity<?> deleteAccount(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) DeleteAccountRequest request) {
        if (!allowReauth(user.getId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(TOO_MANY_REAUTH);
        }
        DeleteAccountRequest proof = request != null ? request : new DeleteAccountRequest();
        userService.deleteAccount(user.getId(), proof.getCurrentPassword(), proof.getGoogleIdToken());
        return ResponseEntity.ok().build();
    }

    // Unsubscribe is deliberately two-step: the GET only renders a confirmation
    // form and the mutation happens on POST. Corporate mail gateways (Safe Links,
    // Proofpoint, …) prefetch every link in an email — a mutating GET would let
    // that scan silently unsubscribe the reader. The token travels in a hidden
    // input; Thymeleaf's th:value escapes it, so the page is safe whatever the
    // query string carried.
    @GetMapping("/unsubscribe")
    @Operation(summary = "Render the unsubscribe confirmation page (no state change)")
    public ResponseEntity<String> unsubscribeConfirmPage(@RequestParam String token) {
        try {
            userService.validateUnsubscribeToken(token);
            Context context = new Context();
            context.setVariable("token", token);
            return htmlPage(HttpStatus.OK, "unsubscribe-confirm", context);
        } catch (IllegalArgumentException ex) {
            return invalidUnsubscribeLink();
        }
    }

    @PostMapping("/unsubscribe")
    @Operation(summary = "Unsubscribe a user from daily digest emails")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        try {
            userService.unsubscribeByToken(token);
            return htmlPage(HttpStatus.OK, "unsubscribe-done", new Context());
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            return invalidUnsubscribeLink();
        }
    }

    /** One shared per-user bucket: both endpoints guess the same secret. */
    private boolean allowReauth(UUID userId) {
        return rateLimiter.tryConsume("account-reauth:" + userId, REAUTH_CAPACITY, REAUTH_REFILL);
    }

    private ResponseEntity<String> invalidUnsubscribeLink() {
        return htmlPage(HttpStatus.BAD_REQUEST, "unsubscribe-invalid", new Context());
    }

    private ResponseEntity<String> htmlPage(HttpStatus status, String template, Context context) {
        context.setVariable("frontendUrl", frontendUrl);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(templateEngine.process(template, context));
    }
}
