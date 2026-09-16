package com.curio.auth.adapter.persistence;

import com.curio.auth.entity.EmailVerificationCode;
import com.curio.auth.port.out.EmailVerificationCodePort;
import com.curio.auth.repository.EmailVerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailVerificationCodeJpaAdapter implements EmailVerificationCodePort {

    private final EmailVerificationCodeRepository repository;

    @Override
    public Optional<EmailVerificationCode> findByChallengeHash(String challengeHash) {
        return repository.findByChallengeHash(challengeHash);
    }

    @Override
    public EmailVerificationCode save(EmailVerificationCode code) {
        return repository.save(code);
    }

    @Override
    public void delete(EmailVerificationCode code) {
        repository.delete(code);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        repository.deleteByUserId(userId);
    }

    @Override
    public int decrementAttempts(UUID id) {
        if (repository.decrementAttempts(id) == 0) {
            return 0;
        }
        return repository.findById(id)
                .map(EmailVerificationCode::getAttemptsRemaining)
                .orElse(0);
    }

    @Override
    public void markConsumed(UUID id) {
        repository.markConsumed(id, LocalDateTime.now());
    }
}
