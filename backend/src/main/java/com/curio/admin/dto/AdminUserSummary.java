package com.curio.admin.dto;

import com.curio.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of the admin reader roster ({@code GET /api/v1/admin/users}).
 *
 * @param topicsCount how many beats the reader follows (0 when they never saved preferences)
 */
public record AdminUserSummary(
        UUID id,
        String email,
        String fullName,
        int topicsCount,
        Boolean deliveryEnabled,
        LocalDateTime createdAt) {

    public static AdminUserSummary from(User user, int topicsCount) {
        return new AdminUserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                topicsCount,
                user.getDeliveryEnabled(),
                user.getCreatedAt());
    }
}
