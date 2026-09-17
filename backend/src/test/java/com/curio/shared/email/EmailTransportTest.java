package com.curio.shared.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTransportTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplateBuilder restTemplateBuilder;

    private EmailTransport transport;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        transport = new EmailTransport(new ObjectMapper(), restTemplateBuilder);
        ReflectionTestUtils.setField(transport, "resendApiKey", "re_test");
        ReflectionTestUtils.setField(transport, "fromEmail", "no-reply@curio.test");
        transport.init();
    }

    @Test
    void returnsTheProviderMessageId_andSendsFromTheConfiguredAddress() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"id\":\"msg_1\"}"));

        String id = transport.send("to@example.com", "Hi", "<p>hi</p>");

        assertThat(id).isEqualTo("msg_1");
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST),
                argThat((HttpEntity<?> e) -> {
                    @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) e.getBody();
                    return "Curio <no-reply@curio.test>".equals(body.get("from"))
                            && "Bearer re_test".equals(e.getHeaders().getFirst("Authorization"));
                }), eq(String.class));
    }

    @Test
    void retriesRateLimits_andServerErrors() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "429", null, null, null))
                .thenThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "502", null, null, null))
                .thenReturn(ResponseEntity.ok("{\"id\":\"msg_2\"}"));

        assertThat(transport.send("to@example.com", "Hi", "<p>hi</p>")).isEqualTo("msg_2");
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    void failsFastOnOtherClientErrors_withoutSmtpFallbackConfigured() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "403", null, null, null));

        assertThatThrownBy(() -> transport.send("to@example.com", "Hi", "<p>hi</p>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send email");
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }
}
