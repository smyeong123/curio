package com.curio.news.entity;

import com.curio.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "digests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Digest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> content;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "email_provider_id", unique = true)
    private String emailProviderId;

    @Column(name = "email_opened_at")
    private LocalDateTime emailOpenedAt;

    @Column(name = "email_clicked_at")
    private LocalDateTime emailClickedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            // Pinned to UTC: the V23 one-digest-per-user-per-day unique index and
            // the 30-day retention cutoff both treat generated_at as UTC wall time.
            // The ambient JVM zone must never leak into this column (dev machines
            // aren't UTC even though the prod container is).
            generatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
    }
}
