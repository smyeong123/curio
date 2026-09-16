package com.curio.shared.webhook;

import com.curio.shared.security.WebhookSignatureVerifier;
import com.curio.shared.port.in.EmailUseCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "External service webhook handlers")
@Slf4j
public class WebhookController {

    private final EmailUseCase emailService;
    private final WebhookSignatureVerifier webhookSignatureVerifier;
    private final ObjectMapper objectMapper;

    @PostMapping("/email")
    @Operation(summary = "Handle email tracking events from Resend")
    public ResponseEntity<Void> handleEmailWebhook(
            @RequestBody String payload,
            @RequestHeader HttpHeaders headers
    ) {
        if (!webhookSignatureVerifier.verify(payload, headers)) {
            log.warn("Rejected email webhook due to invalid signature");
            return ResponseEntity.badRequest().build();
        }

        try {
            Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {});
            emailService.processWebhookEvent(event);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            log.warn("Rejected email webhook due to invalid payload", ex);
            return ResponseEntity.badRequest().build();
        }
    }
}
