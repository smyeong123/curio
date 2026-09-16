package com.curio.user.controller;

import com.curio.user.dto.ChangePasswordRequest;
import com.curio.user.dto.PreferencesRequest;
import com.curio.user.dto.UpdateProfileRequest;
import com.curio.user.dto.UserResponse;
import com.curio.user.entity.User;
import com.curio.user.port.in.UserUseCase;
import com.curio.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile and preferences")
public class UserController {

    private final UserUseCase userService;

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
    public ResponseEntity<Map<String, Object>> getPreferences(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getPreferencesDetail(user.getId()));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update user topic preferences (and optional timezone/deliveryHour)")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PreferencesRequest request) {
        userService.updatePreferences(user.getId(), request);
        return ResponseEntity.ok(userService.getPreferencesDetail(user.getId()));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change user password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete user account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user) {
        userService.deleteAccount(user.getId());
        return ResponseEntity.ok().build();
    }

    // Unsubscribe is deliberately two-step: the GET only renders a confirmation
    // form and the mutation happens on POST. Corporate mail gateways (Safe Links,
    // Proofpoint, …) prefetch every link in an email — a mutating GET would let
    // that scan silently unsubscribe the reader. Tokens that pass validation are
    // base64url + '.' only, so embedding one in the form is HTML-safe.
    @GetMapping("/unsubscribe")
    @Operation(summary = "Render the unsubscribe confirmation page (no state change)")
    public ResponseEntity<String> unsubscribeConfirmPage(@RequestParam String token) {
        try {
            userService.validateUnsubscribeToken(token);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body("""
                            <html><body style="font-family:Arial,sans-serif;padding:24px;">
                            <h2>Unsubscribe from Curio?</h2>
                            <p>You will no longer receive daily Curio digest emails.</p>
                            <form method="POST" action="/api/v1/user/unsubscribe">
                            <input type="hidden" name="token" value="%s">
                            <button type="submit" style="padding:10px 18px;font-size:15px;cursor:pointer;">Yes, unsubscribe me</button>
                            </form>
                            </body></html>
                            """.formatted(token));
        } catch (IllegalArgumentException ex) {
            return invalidUnsubscribeLink();
        }
    }

    @PostMapping("/unsubscribe")
    @Operation(summary = "Unsubscribe a user from daily digest emails")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        try {
            userService.unsubscribeByToken(token);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body("""
                            <html><body style="font-family:Arial,sans-serif;padding:24px;">
                            <h2>You are unsubscribed.</h2>
                            <p>You will no longer receive daily Curio digest emails.</p>
                            </body></html>
                            """);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            return invalidUnsubscribeLink();
        }
    }

    private ResponseEntity<String> invalidUnsubscribeLink() {
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body("""
                        <html><body style="font-family:Arial,sans-serif;padding:24px;">
                        <h2>Invalid unsubscribe link.</h2>
                        <p>Please try again from a newer email.</p>
                        </body></html>
                        """);
    }
}
