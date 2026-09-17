package com.curio.shared.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks scheduled job run history in Redis so status persists across server restarts.
 * Keys use pattern: admin:job:status:{jobName}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobStatusRegistry {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "admin:job:status:";

    public void recordSuccess(String jobName, Map<String, Object> result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("jobName", jobName);
        record.put("lastRanAt", LocalDateTime.now().toString());
        record.put("lastStatus", "SUCCESS");
        record.put("lastResult", result != null ? result : Map.of());
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jobName, record);
        } catch (Exception e) {
            log.warn("Failed to persist job status for {}: {}", jobName, e.getMessage());
        }
    }

    public void recordFailure(String jobName, String errorMessage) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("jobName", jobName);
        record.put("lastRanAt", LocalDateTime.now().toString());
        record.put("lastStatus", "FAILED");
        record.put("lastResult", Map.of("error", errorMessage != null ? errorMessage : "Unknown error"));
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jobName, record);
        } catch (Exception e) {
            log.warn("Failed to persist job failure status for {}: {}", jobName, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getStatus(String jobName) {
        try {
            Object value = redisTemplate.opsForValue().get(KEY_PREFIX + jobName);
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        } catch (Exception e) {
            log.warn("Failed to read job status for {}: {}", jobName, e.getMessage());
        }
        return null;
    }
}
