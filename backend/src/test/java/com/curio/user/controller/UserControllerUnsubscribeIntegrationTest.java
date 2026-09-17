package com.curio.user.controller;

import com.curio.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerUnsubscribeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getRendersConfirmationPageWithoutUnsubscribing() throws Exception {
        doNothing().when(userService).validateUnsubscribeToken("valid-token");

        mockMvc.perform(get("/api/v1/user/unsubscribe")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unsubscribe from Curio?")));

        // Mail-gateway link prefetchers follow GETs — the GET must never mutate.
        verify(userService, never()).unsubscribeByToken(anyString());
    }

    @Test
    void getEchoesTheTokenInAHiddenInput_escaped() throws Exception {
        String hostile = "abc\"><script>alert(1)</script>";
        doNothing().when(userService).validateUnsubscribeToken(hostile);

        mockMvc.perform(get("/api/v1/user/unsubscribe")
                        .param("token", hostile))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<input type=\"hidden\" name=\"token\" value=\"abc&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;\">")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>"))));
    }

    @Test
    void getReturnsBadRequestForInvalidToken() throws Exception {
        doThrow(new IllegalArgumentException("Invalid unsubscribe link."))
                .when(userService).validateUnsubscribeToken("invalid-token");

        mockMvc.perform(get("/api/v1/user/unsubscribe")
                        .param("token", "invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid unsubscribe link.")));
    }

    @Test
    void postUnsubscribesWithoutAuthentication() throws Exception {
        doNothing().when(userService).unsubscribeByToken("valid-token");

        mockMvc.perform(post("/api/v1/user/unsubscribe")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You are unsubscribed.")));

        verify(userService).unsubscribeByToken("valid-token");
    }

    @Test
    void postReturnsBadRequestForInvalidToken() throws Exception {
        doThrow(new IllegalArgumentException("Invalid unsubscribe link."))
                .when(userService).unsubscribeByToken("invalid-token");

        mockMvc.perform(post("/api/v1/user/unsubscribe")
                        .param("token", "invalid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid unsubscribe link.")));
    }
}
