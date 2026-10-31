package com.curio.user.service;

import com.curio.auth.port.in.GoogleReauthUseCase;
import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.shared.exception.UnauthorizedException;
import com.curio.shared.security.UnsubscribeTokenService;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Attack: someone holding only a victim's access token (15 min — XSS, a shared
 * computer) calls DELETE /user/me and irreversibly destroys the account.
 * Deletion must demand the password (or, for Google-only accounts, a fresh
 * Google ID token for the same Google account).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDeleteAccountTest {

    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private UnsubscribeTokenService unsubscribeTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private GoogleReauthUseCase googleReauth;

    @InjectMocks
    private UserService userService;

    private User passwordUser() {
        return User.builder().id(UUID.randomUUID()).email("p@e.com").passwordHash("HASH").build();
    }

    private User googleOnlyUser() {
        return User.builder().id(UUID.randomUUID()).email("g@e.com").googleId("google-sub-1").build();
    }

    @Test
    void passwordAccount_withoutPassword_isNotDeleted() {
        User user = passwordUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deleteAccount(user.getId(), null, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).delete(any());
    }

    @Test
    void passwordAccount_wrongPassword_isNotDeleted() {
        User user = passwordUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("guess", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(user.getId(), "guess", null))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).delete(any());
    }

    @Test
    void passwordAccount_googleTokenDoesNotSubstituteForPassword() {
        User user = passwordUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deleteAccount(user.getId(), null, "some-google-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).delete(any());
    }

    @Test
    void passwordAccount_correctPassword_isDeleted() {
        User user = passwordUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right", "HASH")).thenReturn(true);

        userService.deleteAccount(user.getId(), "right", null);

        verify(userPort).delete(user);
    }

    @Test
    void googleOnlyAccount_withoutFreshGoogleProof_isNotDeleted() {
        User user = googleOnlyUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));
        when(googleReauth.confirmsAccount(null, "google-sub-1")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(user.getId(), null, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).delete(any());
    }

    @Test
    void googleOnlyAccount_tokenForAnotherGoogleAccount_isNotDeleted() {
        User user = googleOnlyUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));
        when(googleReauth.confirmsAccount("attacker-token", "google-sub-1")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(user.getId(), null, "attacker-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(userPort, never()).delete(any());
    }

    @Test
    void googleOnlyAccount_confirmedByGoogle_isDeleted() {
        User user = googleOnlyUser();
        when(userPort.findById(user.getId())).thenReturn(Optional.of(user));
        when(googleReauth.confirmsAccount("fresh-token", "google-sub-1")).thenReturn(true);

        userService.deleteAccount(user.getId(), null, "fresh-token");

        verify(userPort).delete(user);
    }
}
