package com.curio.admin.controller;

import com.curio.admin.dto.StatsResponse;
import com.curio.admin.service.AdminDigestService;
import com.curio.admin.service.AdminOperationsService;
import com.curio.admin.service.AdminStatsService;
import com.curio.admin.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private AdminDigestService adminDigestService;

    @MockBean
    private AdminStatsService adminStatsService;

    @MockBean
    private AdminOperationsService adminOperationsService;

    @Test
    void adminStatsRejectsAnonymousUser() throws Exception {
        int statusCode = mockMvc.perform(get("/api/v1/admin/stats"))
                .andReturn()
                .getResponse()
                .getStatus();

        // Exactly 401 (not 403) is load-bearing: the SPA's axios interceptor only
        // triggers the silent cookie refresh on 401, so an expired access token
        // answered with 403 would strand the session until a full page reload.
        org.assertj.core.api.Assertions.assertThat(statusCode).isEqualTo(401);
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminStatsRejectsNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminStatsAllowsAdminUser() throws Exception {
        when(adminStatsService.getStats()).thenReturn(StatsResponse.builder()
                .totalUsers(10L)
                .emailsSentToday(7L)
                .quizCompletionsToday(5L)
                .build());

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.emailsSentToday").value(7))
                .andExpect(jsonPath("$.quizCompletionsToday").value(5));
    }
}
