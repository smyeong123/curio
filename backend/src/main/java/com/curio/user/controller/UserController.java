package com.curio.user.controller;

import com.curio.user.dto.ChangePasswordRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile and preferences")
public class UserController {

    private final UserUseCase userService;
    /** Renders the server-side unsubscribe pages (templates/unsubscribe-*.html). */
    private final TemplateEngine templateEngine;

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

    private ResponseEntity<String> invalidUnsubscribeLink() {
        return htmlPage(HttpStatus.BAD_REQUEST, "unsubscribe-invalid", new Context());
    }

    private ResponseEntity<String> htmlPage(HttpStatus status, String template, Context context) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(templateEngine.process(template, context));
    }
}
