package com.curio.user.service;

import com.curio.auth.port.out.RefreshTokenPort;
import com.curio.shared.security.UnsubscribeTokenService;
import com.curio.shared.config.TopicConstants;
import com.curio.shared.i18n.Language;
import com.curio.user.dto.PreferencesRequest;
import com.curio.user.dto.PreferencesResponse;
import com.curio.user.dto.UserResponse;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.in.UserUseCase;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements UserUseCase {

    private final UserPort userPort;
    private final UserPreferencesPort userPreferencesPort;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenPort refreshTokenPort;

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, String fullName, Boolean deliveryEnabled) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (fullName != null) {
            user.setFullName(fullName);
        }
        if (deliveryEnabled != null) {
            user.setDeliveryEnabled(deliveryEnabled);
        }

        user = userPort.save(user);
        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PreferencesResponse getPreferencesDetail(UUID userId) {
        return userPreferencesPort.findByUserId(userId)
                .map(PreferencesResponse::from)
                .orElseGet(PreferencesResponse::defaults);
    }

    @Transactional
    public String[] updatePreferences(UUID userId, PreferencesRequest request) {
        List<String> invalid = request.getTopics().stream()
                .filter(t -> !TopicConstants.VALID_TOPICS.contains(t))
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Invalid topics: " + invalid);
        }

        // The UI can't produce duplicates but the API can; a repeated topic would
        // put the same stories in the digest twice. The DTO's min-3 rule must hold
        // for DISTINCT topics, so re-check after de-duplication.
        String[] distinctTopics = request.getTopics().stream().distinct().toArray(String[]::new);
        if (distinctTopics.length < 3) {
            throw new IllegalArgumentException("Please select at least 3 distinct topics");
        }

        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserPreferences preferences = userPreferencesPort.findByUserId(userId)
                .orElse(UserPreferences.builder().user(user).build());

        preferences.setTopics(distinctTopics);
        if (request.getTimezone() != null) {
            // Validate the zone id; reject obviously bogus values early.
            try {
                java.time.ZoneId.of(request.getTimezone());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid timezone: " + request.getTimezone());
            }
            preferences.setTimezone(request.getTimezone());
        }
        if (request.getDeliveryHour() != null) {
            preferences.setDeliveryHour(request.getDeliveryHour());
        }
        if (request.getTimezoneAuto() != null) {
            preferences.setTimezoneAuto(request.getTimezoneAuto());
        }
        if (request.getLanguage() != null) {
            // The DTO already rejects anything but en/ko at the API edge; re-check here so
            // internal callers can't slip an unsupported edition into the column either.
            if (!Language.isSupportedCode(request.getLanguage())) {
                throw new IllegalArgumentException("Invalid language: " + request.getLanguage());
            }
            preferences.setLanguage(Language.fromCode(request.getLanguage()).code());
        }
        userPreferencesPort.save(preferences);

        return preferences.getTopics();
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException("Password change is not available for OAuth accounts");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userPort.save(user);

        // Revoke all sessions so old refresh tokens die with the old password
        refreshTokenPort.deleteByUserId(userId);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userPort.delete(user);
    }

    @Transactional
    public void unsubscribe(UUID userId) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setDeliveryEnabled(false);
        userPort.save(user);
    }

    @Transactional
    public void unsubscribeByToken(String token) {
        UUID userId = unsubscribeTokenService.validateAndExtractUserId(token);
        unsubscribe(userId);
    }

    public void validateUnsubscribeToken(String token) {
        unsubscribeTokenService.validateAndExtractUserId(token);
    }
}
