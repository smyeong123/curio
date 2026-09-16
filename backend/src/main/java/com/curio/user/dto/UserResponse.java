package com.curio.user.dto;

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
}
