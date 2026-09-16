package com.curio.user.adapter.persistence;

import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPreferencesPort;
import com.curio.user.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPreferencesJpaAdapter implements UserPreferencesPort {

    private final UserPreferencesRepository repository;

    @Override
    public Optional<UserPreferences> findByUserId(UUID userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public List<UserPreferences> findByUserIdIn(List<UUID> userIds) {
        return repository.findByUserIdIn(userIds);
    }

    @Override
    public List<UserPreferences> findAll() {
        return repository.findAll();
    }

    @Override
    public UserPreferences save(UserPreferences preferences) {
        return repository.save(preferences);
    }
}
