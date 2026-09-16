package com.curio.news.dto;

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
public class DigestResponse {
    private UUID id;
    private Map<String, Object> content;
    private LocalDateTime generatedAt;
    private LocalDateTime emailSentAt;
}
