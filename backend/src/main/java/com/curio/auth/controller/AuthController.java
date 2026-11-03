package com.curio.auth.controller;

import com.curio.auth.dto.ConfirmEmailRequest;
import com.curio.auth.dto.ForgotPasswordRequest;
import com.curio.auth.dto.GoogleLoginRequest;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.ResetPasswordRequest;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.RegisterResponse;
import com.curio.auth.dto.ResendConfirmationRequest;
import com.curio.auth.dto.ResendCodeRequest;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;
import com.curio.auth.dto.AuthResponse;
import com.curio.auth.port.in.AuthUseCase;
import com.curio.shared.security.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {

    private final AuthUseCase authService;
    private final CookieUtils cookieUtils;

    @PostMapping("/register")
    @Operation(summary = "Start a sign-up: emails a confirmation link (no session until it is opened)")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        // 202 with one body for every case (new / confirmed / unconfirmed address).
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.register(request));
    }

    @PostMapping("/confirm-email")
    @Operation(summary = "Open a sign-up confirmation link: confirms the email and signs in")
    public ResponseEntity<AuthResponse> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        AuthResponse response = authService.confirmEmail(request.token());
        return withRefreshTokenCookie(response);
    }

    @PostMapping("/resend-confirmation")
    @Operation(summary = "Email a fresh sign-up confirmation link (always 202)")
    public ResponseEntity<RegisterResponse> resendConfirmation(@Valid @RequestBody ResendConfirmationRequest request) {
        authService.resendConfirmation(request.email(), request.language());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(RegisterResponse.confirmationSent());
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password (step 1 — emails a verification code)")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        // Verification disabled → a session was issued immediately; set the cookie.
        if (response.getAuth() != null) {
            String refreshToken = response.getAuth().getRefreshToken();
            response.getAuth().setRefreshToken(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshTokenCookie(refreshToken).toString())
                    .body(response);
        }
        // Verification required → no session yet, just the challenge.
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-code")
    @Operation(summary = "Verify the emailed login code (step 2 of email/password login)")
    public ResponseEntity<VerifyCodeResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        VerifyCodeResponse response = authService.verifyCode(request);
        if (response.getAuth() != null) {
            String refreshToken = response.getAuth().getRefreshToken();
            response.getAuth().setRefreshToken(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshTokenCookie(refreshToken).toString())
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-code")
    @Operation(summary = "Resend the login verification code")
    public ResponseEntity<ResendCodeResponse> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        return ResponseEntity.ok(authService.resendCode(request));
    }

    @PostMapping("/google")
    @Operation(summary = "Login with Google ID token")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.googleLogin(request.getToken());
        return withRefreshTokenCookie(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String cookieToken,
            @RequestBody(required = false) Map<String, String> body) {
        // Support both cookie-based and body-based refresh for backward compatibility
        String refreshToken = cookieToken;
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        return withRefreshTokenCookie(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String cookieToken,
            @RequestBody(required = false) Map<String, String> body) {
        String refreshToken = cookieToken;
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredRefreshTokenCookie().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists for this email, a reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
    }

    private ResponseEntity<AuthResponse> withRefreshTokenCookie(AuthResponse response) {
        String refreshToken = response.getRefreshToken();
        // Clear refresh token from response body — it's in the cookie now
        response.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshTokenCookie(refreshToken).toString())
                .body(response);
    }
}
