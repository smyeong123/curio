package com.curio.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "topics", columnDefinition = "text[]")
    @Builder.Default
    private String[] topics = new String[0];

    /** IANA timezone id (e.g. "America/Los_Angeles"). UTC if null. */
    @Column(name = "timezone", length = 64)
    private String timezone;

    /** Hour of day in {@link #timezone} when the digest email should land (0-23). 8 if null. */
    @Column(name = "delivery_hour")
    private Integer deliveryHour;

    /**
     * When true (default), {@link #timezone} auto-follows the user's device on each app
     * entry so a traveler's digest tracks their current location. When false, the user
     * has pinned a fixed timezone in Settings and it is never auto-changed.
     */
    @Column(name = "timezone_auto", nullable = false)
    @Builder.Default
    private boolean timezoneAuto = true;

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
