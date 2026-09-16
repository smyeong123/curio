package com.curio.user.port.out;

import com.curio.user.entity.UserPreferences;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for user-preferences persistence.
 * Domain services depend on this interface; the JPA adapter
 * (UserPreferencesRepository) is the infrastructure implementation.
 */
public interface UserPreferencesPort {

    Optional<UserPreferences> findByUserId(UUID userId);

    List<UserPreferences> findByUserIdIn(List<UUID> userIds);

    List<UserPreferences> findAll();

    UserPreferences save(UserPreferences preferences);
}
