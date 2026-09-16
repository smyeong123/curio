package com.curio.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bring-Your-Own-Key entry. One row per (user, provider).
 *
 * The encrypted_key + key_iv columns hold AES-256-GCM ciphertext + nonce.
 * They are intentionally annotated @JsonIgnore so even an accidental
 * controller leak (e.g. returning the entity) cannot expose the key.
 */
@Entity
@Table(name = "user_api_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserApiKey {

    public enum Provider {
        CLAUDE, GEMINI, OPENAI;

        /**
         * Maps the {@code ai.provider} config value (claude/gemini/openai) to a
         * Provider. Unknown or null values default to CLAUDE, mirroring the
         * platform's default AI provider.
         */
        public static Provider fromConfigName(String name) {
            if (name == null) return CLAUDE;
            return switch (name.toLowerCase()) {
                case "gemini" -> GEMINI;
                case "openai" -> OPENAI;
                default -> CLAUDE;
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @JsonIgnore
    @Column(name = "encrypted_key", nullable = false)
    private byte[] encryptedKey;

    @JsonIgnore
    @Column(name = "key_iv", nullable = false)
    private byte[] keyIv;

    /** "sk-ant-...XYZW" — safe to show. Never includes the secret middle. */
    @Column(name = "key_preview", nullable = false, length = 64)
    private String keyPreview;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (rotatedAt == null) {
            rotatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * True if the key has not been rotated in the last 90 days. UI surfaces this
     * as a soft warning; the key continues to work until the user rotates it.
     */
    @Transient
    public boolean isStale() {
        if (rotatedAt == null) return false;
        return rotatedAt.isBefore(LocalDateTime.now().minusDays(90));
    }
}
