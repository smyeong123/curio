package com.curio.user.service;

import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.auth.service.UnsubscribeTokenService;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServicePasswordTest {

    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private UnsubscribeTokenService unsubscribeTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenPort refreshTokenPort;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .fullName("Test User")
                .isAdmin(false)
                .deliveryEnabled(true)
                .emailVerified(true)
                .build();
    }

    @Test
    void changePassword_succeeds_withCorrectCurrentPassword() {
        when(userPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currentPass", "$2a$10$hashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newHash");
        when(userPort.save(any())).thenReturn(user);

        userService.changePassword(userId, "currentPass", "newPassword123");

        verify(passwordEncoder).encode("newPassword123");
        verify(userPort).save(user);
        verify(refreshTokenPort).deleteByUserId(userId);
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newHash");
    }

    @Test
    void changePassword_throwsUnauthorized_whenCurrentPasswordWrong() {
        when(userPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, "wrongPass", "newPassword123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userPort, never()).save(any());
        verify(refreshTokenPort, never()).deleteByUserId(any());
    }

    @Test
    void changePassword_throwsUnauthorized_forOAuthOnlyAccount() {
        user.setPasswordHash(null);
        when(userPort.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(userId, "anything", "newPassword123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("OAuth");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userPort, never()).save(any());
    }

    @Test
    void changePassword_throwsResourceNotFound_whenUserDoesNotExist() {
        when(userPort.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(userId, "current", "new12345"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
