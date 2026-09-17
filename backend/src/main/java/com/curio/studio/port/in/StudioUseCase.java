package com.curio.studio.port.in;

import com.curio.studio.dto.StudioStatusResponse;
import com.curio.studio.dto.TaskStatus;
import com.curio.user.entity.User;

/**
 * Inbound port for the self-serve Digest Studio. The controller (driving
 * adapter) depends on this interface, not on the concrete StudioService.
 */
public interface StudioUseCase {

    /** Start (or no-op if already running) digest generation for the user; returns the task's status. */
    TaskStatus startDigestGeneration(User user);

    /** Start (or no-op if already running) emailing the user's latest unsent digest; returns the task's status. */
    TaskStatus startEmailSend(User user);

    /** Combined live status of both tasks plus studio context (topics, latest digest). */
    StudioStatusResponse getStatus(User user);
}
