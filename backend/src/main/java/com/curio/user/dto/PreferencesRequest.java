package com.curio.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PreferencesRequest {

    @NotEmpty(message = "At least one topic must be selected")
    @Size(min = 3, max = 18, message = "Please select between 3 and 18 topics")
    private List<String> topics;

    /** Optional IANA timezone id, e.g. "America/Los_Angeles". Null = UTC. */
    private String timezone;

    /** Optional preferred delivery hour 0-23 in the chosen timezone. Null = 8. */
    @Min(value = 0, message = "deliveryHour must be 0-23")
    @Max(value = 23, message = "deliveryHour must be 0-23")
    private Integer deliveryHour;

    /**
     * Optional. true = timezone auto-follows the device (default); false = the user
     * pinned a fixed timezone. Null leaves the current setting unchanged.
     */
    private Boolean timezoneAuto;

    /**
     * Optional edition for the digest, quiz and daily email: "en" or "ko". Null leaves
     * the current setting unchanged. Validated strictly here; UserService normalizes
     * the case before storing.
     */
    @Pattern(regexp = "(?i)en|ko", message = "language must be 'en' or 'ko'")
    private String language;
}
