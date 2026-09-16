package com.curio.admin.port.in;

import com.curio.admin.dto.AdminDigestResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface AdminDigestUseCase {
    Page<AdminDigestResponse> getDigests(int page, int size, String topic, String userEmail);
    Map<String, Object> triggerDigestGeneration();
    Map<String, Object> triggerDigestGeneration(List<String> topicFilter);
}
