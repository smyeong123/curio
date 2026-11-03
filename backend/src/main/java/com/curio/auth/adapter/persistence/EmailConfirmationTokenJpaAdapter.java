package com.curio.auth.adapter.persistence;

import com.curio.auth.entity.EmailConfirmationToken;
import com.curio.auth.port.out.EmailConfirmationTokenPort;
import com.curio.auth.repository.EmailConfirmationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailConfirmationTokenJpaAdapter implements EmailConfirmationTokenPort {

    private final EmailConfirmationTokenRepository repository;

    @Override
    public Optional<EmailConfirmationToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }

    @Override
    public EmailConfirmationToken save(EmailConfirmationToken confirmationToken) {
        return repository.save(confirmationToken);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repository.deleteByUserId(userId);
    }

    @Override
    public int markUsed(UUID id, LocalDateTime usedAt) {
        return repository.markUsed(id, usedAt);
    }
}
