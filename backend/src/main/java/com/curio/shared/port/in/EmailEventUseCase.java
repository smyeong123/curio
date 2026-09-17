package com.curio.shared.port.in;

import java.util.Map;

/**
 * Inbound port for delivery events pushed back by the email provider (opens,
 * clicks, bounces, complaints). The webhook controller depends on this, not on
 * the processor implementation.
 */
public interface EmailEventUseCase {

    /** One provider event payload (already signature-verified by the controller). */
    void processWebhookEvent(Map<String, Object> event);
}
