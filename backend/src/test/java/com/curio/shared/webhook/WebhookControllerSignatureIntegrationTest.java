package com.curio.shared.webhook;

import com.curio.shared.port.in.EmailEventUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerSignatureIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_dGVzdC13ZWJob29rLXNlY3JldA==";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailEventUseCase emailService;

    @Test
    void rejectsWebhookWithInvalidSignature() throws Exception {
        String payload = "{\"type\":\"email.opened\",\"data\":{\"email_id\":\"msg_123\"}}";

        mockMvc.perform(post("/api/v1/webhooks/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("svix-id", "msg_1")
                        .header("svix-timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("svix-signature", "v1,invalid-signature")
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailService);
    }

    @Test
    void acceptsWebhookWithValidSignature() throws Exception {
        String payload = "{\"type\":\"email.opened\",\"data\":{\"email_id\":\"msg_123\"}}";
        String messageId = "msg_1";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = signForSvixHeader(messageId, timestamp, payload, WEBHOOK_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("svix-id", messageId)
                        .header("svix-timestamp", timestamp)
                        .header("svix-signature", "v1," + signature)
                        .content(payload))
                .andExpect(status().isOk());

        verify(emailService).processWebhookEvent(anyMap());
    }

    private String signForSvixHeader(String messageId, String timestamp, String payload, String secret) throws Exception {
        String normalizedSecret = secret.startsWith("whsec_") ? secret.substring(6) : secret;
        byte[] key = Base64.getDecoder().decode(normalizedSecret);

        String signedPayload = messageId + "." + timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        byte[] signature = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(signature);
    }
}
