package com.curio.user.dto;

import com.curio.user.entity.UserApiKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Safe-to-serialize view of a stored BYOK entry.
 * Critically: does NOT include the encrypted bytes or the plaintext key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeySummary {
    private UserApiKey.Provider provider;
    /** "sk-ant-...XYZW" — masked for UI. */
    private String keyPreview;
    private boolean validated;
    private LocalDateTime validatedAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime rotatedAt;
    /** True when the key has not been rotated in 90+ days. UI shows a soft warning. */
    private boolean stale;
}
