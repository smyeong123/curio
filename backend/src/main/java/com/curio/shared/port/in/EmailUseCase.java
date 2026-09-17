package com.curio.shared.port.in;

import com.curio.news.entity.Digest;
import com.curio.user.entity.User;

/**
 * Inbound port for the emails Curio sends. Jobs, batches, Studio and the auth
 * flow (driving adapters) depend on this interface, not on EmailService.
 * Provider delivery events arrive through {@link EmailEventUseCase}.
 */
public interface EmailUseCase {

    /**
     * What happened to a digest send. Losing the atomic email claim to a
     * concurrent sender (hourly job vs admin batch vs Studio) is a normal
     * outcome, but callers must not report it as their own successful send —
     * the winner's attempt may still fail and release the claim for retry.
     */
    enum DigestSendOutcome { SENT, ALREADY_CLAIMED }

    /**
     * @return {@link DigestSendOutcome#SENT} if this call delivered the email,
     *         {@link DigestSendOutcome#ALREADY_CLAIMED} if another path holds
     *         (or completed) the send claim for this digest.
     * @throws RuntimeException if this call claimed the digest but the provider
     *         send failed (the claim is released for retry before rethrowing).
     */
    DigestSendOutcome sendDigestEmail(User user, Digest digest);

    void sendPasswordResetEmail(User user, String resetToken);

    /** The one-time sign-in code; {@code ttlMinutes} is quoted in the copy. */
    void sendLoginVerificationEmail(User user, String code, long ttlMinutes);
}
