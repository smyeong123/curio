package com.curio.admin.port.in;

import com.curio.admin.dto.AdminUserDetail;
import com.curio.admin.dto.AdminUserSummary;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AdminUserUseCase {
    Page<AdminUserSummary> getUsers(int page, int size, String search);
    AdminUserDetail getUserDetail(UUID userId);
}
