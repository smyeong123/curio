package com.curio.user.dto;

import com.curio.user.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;

    @JsonProperty("isAdmin")
    private Boolean isAdmin;

    @JsonProperty("deliveryEnabled")
    private Boolean deliveryEnabled;

    @JsonProperty("emailVerified")
    private Boolean emailVerified;

    private LocalDateTime createdAt;

    /** True if the user has a password set (i.e. not a Google OAuth-only account). */
    @JsonProperty("hasPassword")
    private Boolean hasPassword;

    /** The profile as the API exposes it — the hash itself never leaves the entity. */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isAdmin(user.getIsAdmin())
                .deliveryEnabled(user.getDeliveryEnabled())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .hasPassword(user.getPasswordHash() != null)
                .build();
    }
}
