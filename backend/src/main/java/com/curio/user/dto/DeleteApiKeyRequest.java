package com.curio.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class DeleteApiKeyRequest {
    @NotBlank(message = "currentPassword is required")
    @ToString.Exclude   // never let the password reach a log / toString dump
    private String currentPassword;
}
