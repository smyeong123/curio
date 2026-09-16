package com.curio.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendCodeRequest {

    @NotBlank(message = "Challenge id is required")
    private String challengeId;
}
