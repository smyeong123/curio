package com.curio.studio.port.in;

import com.curio.user.entity.User;

import java.util.Map;

/**
 * Inbound port for the self-serve Digest Studio. The controller (driving
 * adapter) depends on this interface, not on the concrete StudioService.
 */
public interface StudioUseCase {

    /** Start (or no-op if already running) digest generation for the user. */
    Map<String, Object> startDigestGeneration(User user);

    /** Start (or no-op if already running) emailing the user's latest unsent digest. */
    Map<String, Object> startEmailSend(User user);

    /** Combined live status of both tasks plus studio context (topics, latest digest). */
    Map<String, Object> getStatus(User user);
}
