package com.curio.user.port.in;

import com.curio.user.dto.PreferencesRequest;
import com.curio.user.dto.UserResponse;

import java.util.UUID;

/**
 * Inbound port for user profile and preferences use cases.
 * Controllers and other driving adapters depend on this interface,
 * not on the concrete UserService implementation.
 */
public interface UserUseCase {

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, String fullName, Boolean deliveryEnabled);

    String[] getPreferences(UUID userId);

    /** Topics + timezone + deliveryHour as a single response payload. */
    java.util.Map<String, Object> getPreferencesDetail(UUID userId);

    String[] updatePreferences(UUID userId, PreferencesRequest request);

    void changePassword(UUID userId, String currentPassword, String newPassword);

    void deleteAccount(UUID userId);

    void unsubscribe(UUID userId);

    void unsubscribeByToken(String token);

    /**
     * Validates an unsubscribe token without mutating anything. Used by the GET
     * confirmation page so that email-gateway link prefetchers can't unsubscribe
     * a user just by following the link; the actual unsubscribe happens on POST.
     */
    void validateUnsubscribeToken(String token);
}
