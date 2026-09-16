package com.curio.auth.entity;

import com.curio.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A one-time, short-lived code emailed to a user as the second step of
 * email/password login (email-based 2FA). The opaque {@code challengeHash}
 * is the hash of the challenge id handed back to the client; the
 * {@code codeHash} is the hash of the 6-digit code emailed to the user.
 *
 * <p>Both are stored hashed (never plaintext), mirroring {@link RefreshToken}
 * and {@link PasswordResetToken}. {@code attemptsRemaining} starts at the
 * configured maximum and is decremented on each wrong code; when it reaches
 * zero the challenge is consumed and the user is steered to a password reset.
 */
@Entity
@Table(name = "email_verification_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "challenge_hash", nullable = false, unique = true)
    private String challengeHash;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "attempts_remaining", nullable = false)
    private int attemptsRemaining;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
