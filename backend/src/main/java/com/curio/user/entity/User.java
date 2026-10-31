package com.curio.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "is_admin")
    @Builder.Default
    private Boolean isAdmin = false;

    @Column(name = "delivery_enabled")
    @Builder.Default
    private Boolean deliveryEnabled = true;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * Wrong email-2FA codes since the last successful verification or password
     * reset, across ALL login challenges. Mutated only through atomic UPDATEs
     * in {@code UserRepository} (see V30) — read-only to entity saves, so an
     * unrelated save of a stale User can't overwrite a concurrent increment.
     */
    @Column(name = "login_code_failures", nullable = false, insertable = false, updatable = false)
    @Builder.Default
    private Integer loginCodeFailures = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
