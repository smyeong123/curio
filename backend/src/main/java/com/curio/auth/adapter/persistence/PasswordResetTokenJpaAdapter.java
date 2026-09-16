package com.curio.auth.adapter.persistence;

import com.curio.auth.entity.PasswordResetToken;
import com.curio.auth.port.out.PasswordResetTokenPort;
import com.curio.auth.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PasswordResetTokenJpaAdapter implements PasswordResetTokenPort {

    private final PasswordResetTokenRepository repository;

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }

    @Override
    public PasswordResetToken save(PasswordResetToken resetToken) {
        return repository.save(resetToken);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
