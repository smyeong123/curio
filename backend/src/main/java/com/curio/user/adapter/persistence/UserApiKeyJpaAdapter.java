package com.curio.user.adapter.persistence;

import com.curio.user.entity.UserApiKey;
import com.curio.user.port.out.UserApiKeyPort;
import com.curio.user.repository.UserApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserApiKeyJpaAdapter implements UserApiKeyPort {

    private final UserApiKeyRepository repository;

    @Override
    public Optional<UserApiKey> findByUserIdAndProvider(UUID userId, UserApiKey.Provider provider) {
        return repository.findByUserIdAndProvider(userId, provider);
    }

    @Override
    public List<UserApiKey> findByUserId(UUID userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public UserApiKey save(UserApiKey key) {
        return repository.save(key);
    }

    @Override
    public void delete(UserApiKey key) {
        repository.delete(key);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndProvider(UUID userId, UserApiKey.Provider provider) {
        repository.deleteByUserIdAndProvider(userId, provider);
    }
}
