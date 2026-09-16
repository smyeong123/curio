package com.curio.user.dto;

import com.curio.user.entity.UserApiKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class SaveApiKeyRequest {

    @NotNull(message = "provider is required")
    private UserApiKey.Provider provider;

    @NotBlank(message = "apiKey is required")
    @Size(min = 16, max = 512, message = "apiKey must be 16-512 chars")
    @ToString.Exclude   // never let the plaintext key reach a log / toString dump
    private String apiKey;

    /** Re-auth: caller must supply current password to add/modify. */
    @NotBlank(message = "currentPassword is required")
    @ToString.Exclude   // never let the password reach a log / toString dump
    private String currentPassword;

    // Note: keys are ALWAYS validated against the provider before storage — the
    // old opt-out `validate` flag was accepted-but-ignored and has been removed
    // (unknown JSON properties from older clients are ignored by Jackson).
}
