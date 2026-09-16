package com.curio.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyCodeRequest {

    @NotBlank(message = "Challenge id is required")
    private String challengeId;

    @NotBlank(message = "Verification code is required")
    private String code;
}
