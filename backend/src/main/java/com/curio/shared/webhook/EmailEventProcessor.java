package com.curio.shared.webhook;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.port.in.EmailEventUseCase;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Applies Resend delivery events to our records: opens and clicks stamp the
 * digest they belong to; hard bounces and spam complaints switch the recipient's
 * delivery off so the daily job stops re-selecting them (sender reputation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailEventProcessor implements EmailEventUseCase {

    private final DigestPort digestPort;
    private final UserPort userPort;

    @Override
    public void processWebhookEvent(Map<String, Object> event) {
        String type = (String) event.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) event.get("data");

        if (data == null) return;

        String emailId = (String) data.get("email_id");
        log.info("Processing email webhook event: type={}, emailId={}", type, emailId);

        Digest digest = null;
        if (emailId != null && !emailId.isBlank()) {
            digest = digestPort.findByEmailProviderId(emailId).orElse(null);
        }

        switch (type != null ? type : "") {
            case "email.opened" -> {
                log.info("Email opened: {}", emailId);
                if (digest != null && digest.getEmailOpenedAt() == null) {
                    digest.setEmailOpenedAt(LocalDateTime.now());
                    digestPort.save(digest);
                }
            }
            case "email.clicked" -> {
                log.info("Email clicked: {}", emailId);
                if (digest != null && digest.getEmailClickedAt() == null) {
                    digest.setEmailClickedAt(LocalDateTime.now());
                    digestPort.save(digest);
                }
            }
            case "email.bounced" -> {
                log.warn("Email bounced: {}", emailId);
                // Only hard/permanent bounces suppress delivery. Soft/transient
                // bounces (mailbox full, greylisting) are logged and left alone.
                if (isHardBounce(data)) {
                    suppressDelivery(digest, data, "hard bounce");
                }
            }
            case "email.complained" -> {
                log.warn("Email complaint: {}", emailId);
                // A spam complaint is unambiguous — always suppress.
                suppressDelivery(digest, data, "spam complaint");
            }
            default -> log.debug("Unhandled webhook event type: {}", type);
        }
    }

    /**
     * True only for Resend bounces classified as hard/permanent. When the payload
     * carries no bounce classification we err on the side of NOT suppressing, so a
     * transient failure never disables a valid user's delivery.
     */
    private boolean isHardBounce(Map<String, Object> data) {
        Object bounceObj = data.get("bounce");
        if (!(bounceObj instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bounce = (Map<String, Object>) bounceObj;
        String bounceType = String.valueOf(bounce.getOrDefault("type", ""));
        String bounceSubType = String.valueOf(bounce.getOrDefault("subType", ""));
        return "hard".equalsIgnoreCase(bounceType) || "permanent".equalsIgnoreCase(bounceType)
                || "hard".equalsIgnoreCase(bounceSubType) || "permanent".equalsIgnoreCase(bounceSubType);
    }

    /**
     * Disable digest delivery for the recipient of a hard-failed email. Idempotent:
     * a no-op when the user can't be resolved or delivery is already off.
     */
    private void suppressDelivery(Digest digest, Map<String, Object> data, String reason) {
        User user = null;
        // Prefer the digest owner when the event maps to a known digest. Re-fetch by
        // id (the FK is available without initializing the lazy proxy) so we hold a
        // managed entity to persist.
        if (digest != null && digest.getUser() != null) {
            user = userPort.findById(digest.getUser().getId()).orElse(null);
        }
        // Otherwise resolve by the recipient email carried in the event.
        if (user == null) {
            String email = extractRecipientEmail(data);
            if (email != null && !email.isBlank()) {
                user = userPort.findByEmail(email).orElse(null);
            }
        }
        if (user == null) {
            log.warn("Could not resolve a user to suppress delivery for ({})", reason);
            return;
        }
        if (Boolean.FALSE.equals(user.getDeliveryEnabled())) {
            return;
        }
        user.setDeliveryEnabled(false);
        userPort.save(user);
        log.warn("Disabled digest delivery for user {} due to {}", user.getId(), reason);
    }

    private String extractRecipientEmail(Map<String, Object> data) {
        Object to = data.get("to");
        if (to instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return list.get(0).toString();
        }
        if (to instanceof String s) {
            return s;
        }
        Object email = data.get("email");
        return (email != null) ? email.toString() : null;
    }
}
