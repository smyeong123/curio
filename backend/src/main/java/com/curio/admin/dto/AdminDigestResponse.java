package com.curio.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDigestResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private Map<String, Object> content;
    private LocalDateTime generatedAt;
    private LocalDateTime emailSentAt;
}
