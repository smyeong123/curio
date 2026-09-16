package com.curio.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    /** Optional new display name. Null = leave unchanged. */
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    /** Optional daily-digest email delivery toggle. Null = leave unchanged. */
    private Boolean deliveryEnabled;
}
