package com.curio.auth.controller;

import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.RegisterResponse;
import com.curio.shared.exception.EmailNotConfirmedException;
import com.curio.shared.exception.InvalidConfirmationTokenException;
import com.curio.auth.service.AuthService;
import com.curio.shared.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void forgotPasswordReturnsGenericMessage() throws Exception {
        doNothing().when(authService).requestPasswordReset(anyString());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resetPasswordReturnsSuccessMessage() throws Exception {
        doNothing().when(authService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "test-token",
                                "newPassword", "Password123!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully."));
    }

    @Test
    void googleLoginReturnsAuthPayload() throws Exception {
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("admin@curio.test")
                .fullName("Admin User")
                .isAdmin(true)
                .build();

        when(authService.googleLogin("google-id-token")).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "google-id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.email").value("admin@curio.test"))
                .andExpect(jsonPath("$.isAdmin").value(true))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-token")));
    }

    @Test
    void refreshWithReusedTokenIs401_soTheFrontendNeverStrandsOnA403() throws Exception {
        when(authService.refreshToken("rotated-token"))
                .thenThrow(new UnauthorizedException("Invalid refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "rotated-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // --- sign-up email confirmation ---

    @Test
    void registerIs202_withTheUniformBody_andSetsNoSessionCookie() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(RegisterResponse.confirmationSent());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new@example.com",
                                "password", "Password123!",
                                "fullName", "New User",
                                "language", "ko"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("confirmation_sent"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void confirmEmailIssuesTheSession_likeTheOtherLoginPaths() throws Exception {
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .userId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .email("new@example.com")
                .fullName("New User")
                .isAdmin(false)
                .build();
        when(authService.confirmEmail("link-token")).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "link-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-token")));
    }

    @Test
    void confirmEmailWithABadLinkIs400_withABranchableCode() throws Exception {
        when(authService.confirmEmail("stale")).thenThrow(new InvalidConfirmationTokenException());

        mockMvc.perform(post("/api/v1/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "stale"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_TOKEN_INVALID"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void resendConfirmationIsAlways202() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "anyone@example.com", "language", "en"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("confirmation_sent"));

        verify(authService).resendConfirmation(eq("anyone@example.com"), eq("en"));
    }

    @Test
    void loginToAnUnconfirmedAccountIs403_EmailNotConfirmed_notA401() throws Exception {
        // 403 on purpose: the axios interceptor only tries a silent refresh on 401.
        when(authService.login(any(LoginRequest.class))).thenThrow(new EmailNotConfirmedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "pending@example.com", "password", "Password123!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.message").exists());
    }
}
