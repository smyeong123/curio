package com.curio.admin.port.in;

import org.springframework.data.domain.Page;

import java.util.Map;
import java.util.UUID;

public interface AdminUserUseCase {
    Page<Map<String, Object>> getUsers(int page, int size, String search);
    Map<String, Object> getUserDetail(UUID userId);
}
