package com.curio.user.service;

import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.auth.service.UnsubscribeTokenService;
import com.curio.user.dto.PreferencesRequest;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServicePreferencesTest {

    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private UnsubscribeTokenService unsubscribeTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenPort refreshTokenPort;

    @InjectMocks private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("reader@example.com").build();
        lenient().when(userPort.findById(userId)).thenReturn(Optional.of(user));
    }

    private PreferencesRequest request(String language) {
        PreferencesRequest request = new PreferencesRequest();
        request.setTopics(List.of("Claude (Anthropic)", "DeepSeek", "Reasoning & Context"));
        request.setLanguage(language);
        return request;
    }

    private UserPreferences captureSaved() {
        ArgumentCaptor<UserPreferences> captor = ArgumentCaptor.forClass(UserPreferences.class);
        verify(userPreferencesPort).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void updatePreferences_storesTheEditionLowercased() {
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());

        userService.updatePreferences(userId, request("KO"));

        assertThat(captureSaved().getLanguage()).isEqualTo("ko");
    }

    @Test
    void updatePreferences_leavesTheEditionAlone_whenNotSent() {
        UserPreferences existing = UserPreferences.builder().user(user).language("ko").build();
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(existing));

        userService.updatePreferences(userId, request(null));

        assertThat(captureSaved().getLanguage()).isEqualTo("ko");
    }

    @Test
    void updatePreferences_rejectsAnUnsupportedEdition() {
        assertThatThrownBy(() -> userService.updatePreferences(userId, request("fr")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
        verify(userPreferencesPort, never()).save(any());
    }

    @Test
    void newPreferences_defaultToEnglish() {
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());

        userService.updatePreferences(userId, request(null));

        assertThat(captureSaved().getLanguage()).isEqualTo("en");
    }

    @Test
    void preferencesDetail_exposesTheEdition_andDefaultsToEnglish() {
        when(userPreferencesPort.findByUserId(userId))
                .thenReturn(Optional.of(UserPreferences.builder().user(user).topics(new String[]{"DeepSeek"}).language("ko").build()));
        Map<String, Object> detail = userService.getPreferencesDetail(userId);
        assertThat(detail.get("language")).isEqualTo("ko");

        UUID nobody = UUID.randomUUID();
        when(userPreferencesPort.findByUserId(nobody)).thenReturn(Optional.empty());
        assertThat(userService.getPreferencesDetail(nobody).get("language")).isEqualTo("en");
    }
}
