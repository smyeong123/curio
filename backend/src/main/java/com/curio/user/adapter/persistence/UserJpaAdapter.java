package com.curio.user.adapter.persistence;

import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import com.curio.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserJpaAdapter implements UserPort {

    private final UserRepository repository;

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public Optional<User> findByGoogleId(String googleId) {
        return repository.findByGoogleId(googleId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable) {
        return repository.findByEmailContainingIgnoreCase(email, pageable);
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public List<User> findAllByIds(Collection<UUID> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public Page<User> findByDeliveryEnabledTrue(Pageable pageable) {
        return repository.findByDeliveryEnabledTrue(pageable);
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public User save(User user) {
        return repository.save(user);
    }

    @Override
    public void delete(User user) {
        repository.delete(user);
    }

    @Override
    public User getReferenceById(UUID id) {
        return repository.getReferenceById(id);
    }
}
