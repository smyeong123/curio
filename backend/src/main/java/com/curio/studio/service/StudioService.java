package com.curio.studio.service;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestProgressListener;
import com.curio.news.port.in.NewsUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.studio.port.in.StudioUseCase;
import com.curio.studio.service.StudioTaskStatusService.TaskType;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * User-facing self-serve digest console. Lets a signed-in user generate their
 * own daily digest and send it to themselves on demand — two separate actions,
 * each running on a background thread while the browser polls
 * {@link StudioTaskStatusService} for live progress.
 *
 * This mirrors what the admin batch triggers do, but scoped to the single
 * current user (and billed to their BYOK key, if they've set one).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudioService implements StudioUseCase {

    private final NewsUseCase newsService;
    private final QuizUseCase quizService;
    private final EmailUseCase emailService;
    private final DigestPort digestPort;
    private final UserPort userPort;
    private final UserPreferencesPort userPreferencesPort;
    private final StudioTaskStatusService status;

    @Qualifier("studioExecutor")
    private final Executor studioExecutor;

    // ---- Digest generation -------------------------------------------------

    /**
     * Kick off digest generation for this user on a background thread. If a run
     * is already queued/running, returns the current status without starting a
     * second one (idempotent from the UI's perspective).
     */
    public Map<String, Object> startDigestGeneration(User user) {
        UUID userId = user.getId();
        // isActive() is a fast idempotency check; tryAcquire() is the atomic gate
        // that closes the double-start race (two tabs / double-click / retry).
        if (status.isActive(userId, TaskType.DIGEST) || !status.tryAcquire(userId, TaskType.DIGEST)) {
            return status.get(userId, TaskType.DIGEST);
        }
        // Once the lock is held, every path that fails to hand off to the worker
        // must release it — the worker's own finally covers the handed-off case.
        boolean handedOff = false;
        try {
            status.markQueued(userId, TaskType.DIGEST);
            studioExecutor.execute(() -> {
                try {
                    runDigestGeneration(userId);
                } finally {
                    status.release(userId, TaskType.DIGEST);
                }
            });
            handedOff = true;
        } catch (RejectedExecutionException e) {
            // Studio pool saturated. Surface a retryable failure; the finally below
            // frees the lock so the task never runs inline on the request thread.
            status.markFailed(userId, TaskType.DIGEST,
                    "Studio is busy right now — please try again in a moment.");
        } finally {
            if (!handedOff) {
                status.release(userId, TaskType.DIGEST);
            }
        }
        return status.get(userId, TaskType.DIGEST);
    }

    private void runDigestGeneration(UUID userId) {
        try {
            User user = userPort.findById(userId).orElse(null);
            if (user == null) {
                status.markFailed(userId, TaskType.DIGEST, "Your account could not be loaded.");
                return;
            }

            UserPreferences prefs = userPreferencesPort.findByUserId(userId).orElse(null);
            if (prefs == null || prefs.getTopics() == null || prefs.getTopics().length == 0) {
                status.markSkipped(userId, TaskType.DIGEST,
                        "Pick at least one topic in Settings before generating a digest.");
                return;
            }

            // UTC to match Digest.generatedAt stamping and the V23 unique index's day.
            LocalDateTime startOfDay = LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            if (digestPort.existsByUserIdAndGeneratedAtBetween(userId, startOfDay, endOfDay)) {
                status.markSkipped(userId, TaskType.DIGEST,
                        "You already generated today's digest. You can send it by email below.");
                return;
            }

            status.markRunning(userId, TaskType.DIGEST, "Gathering the news…", 0, prefs.getTopics().length);

            DigestProgressListener listener = (index, total, topic) ->
                    status.markRunning(userId, TaskType.DIGEST,
                            "Summarizing \"" + topic + "\"", index, total);

            Digest digest = newsService.generateDigestForUser(user, listener);

            if (digest == null) {
                // Null can also mean we lost the (user, date) unique-constraint race
                // to a concurrent scheduled/admin run — the digest exists, someone
                // else saved it first. That's a skip, not a scary key error.
                if (digestPort.existsByUserIdAndGeneratedAtBetween(userId, startOfDay, endOfDay)) {
                    status.markSkipped(userId, TaskType.DIGEST,
                            "Today's digest was just generated by the scheduled run. "
                            + "You can send it by email below.");
                    return;
                }
                // Pre-checks passed and no digest exists, so generation itself produced
                // nothing — most often a bad/empty BYOK key or an upstream outage.
                status.markFailed(userId, TaskType.DIGEST,
                        "Couldn't build a digest — the AI provider returned no content. "
                        + "If you're using your own API key, check it's valid and has credit.");
                return;
            }

            // Quiz is best-effort: a failure here shouldn't fail the digest, which
            // is the thing the user actually asked for.
            status.markRunning(userId, TaskType.DIGEST, "Writing your quiz…", 0, 0);
            try {
                quizService.generateQuizForDigest(digest);
            } catch (Exception e) {
                log.warn("Quiz generation failed for studio digest {} (user {}): {}",
                        digest.getId(), userId, e.getMessage());
            }

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("digestId", digest.getId().toString());
            status.markSuccess(userId, TaskType.DIGEST,
                    "Your digest is ready. Send it to your inbox below.", extra);

        } catch (Exception e) {
            log.error("Studio digest generation failed for user {}", userId, e);
            status.markFailed(userId, TaskType.DIGEST,
                    "Something went wrong while generating your digest. Please try again.");
        }
    }

    // ---- Email send --------------------------------------------------------

    public Map<String, Object> startEmailSend(User user) {
        UUID userId = user.getId();
        // isActive() is a fast idempotency check; tryAcquire() is the atomic gate
        // that closes the double-send race (the same unsent digest emailed twice).
        if (status.isActive(userId, TaskType.EMAIL) || !status.tryAcquire(userId, TaskType.EMAIL)) {
            return status.get(userId, TaskType.EMAIL);
        }
        boolean handedOff = false;
        try {
            status.markQueued(userId, TaskType.EMAIL);
            studioExecutor.execute(() -> {
                try {
                    runEmailSend(userId);
                } finally {
                    status.release(userId, TaskType.EMAIL);
                }
            });
            handedOff = true;
        } catch (RejectedExecutionException e) {
            status.markFailed(userId, TaskType.EMAIL,
                    "Studio is busy right now — please try again in a moment.");
        } finally {
            if (!handedOff) {
                status.release(userId, TaskType.EMAIL);
            }
        }
        return status.get(userId, TaskType.EMAIL);
    }

    private void runEmailSend(UUID userId) {
        try {
            User user = userPort.findById(userId).orElse(null);
            if (user == null) {
                status.markFailed(userId, TaskType.EMAIL, "Your account could not be loaded.");
                return;
            }

            status.markRunning(userId, TaskType.EMAIL, "Looking for an unsent digest…", 0, 0);

            Optional<Digest> unsent =
                    digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(userId);
            if (unsent.isEmpty()) {
                status.markSkipped(userId, TaskType.EMAIL,
                        "No unsent digest found. Generate today's digest first.");
                return;
            }

            status.markRunning(userId, TaskType.EMAIL, "Sending to " + user.getEmail() + "…", 0, 0);
            EmailUseCase.DigestSendOutcome outcome = emailService.sendDigestEmail(user, unsent.get());

            if (outcome == EmailUseCase.DigestSendOutcome.SENT) {
                status.markSuccess(userId, TaskType.EMAIL,
                        "Sent! Check " + user.getEmail() + ".", Map.of());
            } else {
                // Another path (hourly job / admin batch) holds the send claim. Its
                // attempt may still fail and release the claim, so don't claim "Sent!"
                // for an email this run didn't deliver.
                status.markSkipped(userId, TaskType.EMAIL,
                        "This digest is already being delivered by another run — "
                        + "check your inbox shortly.");
            }

        } catch (Exception e) {
            log.error("Studio email send failed for user {}", userId, e);
            status.markFailed(userId, TaskType.EMAIL,
                    "Couldn't send the email. The mail service may be unavailable — please try again later.");
        }
    }

    // ---- Status / overview -------------------------------------------------

    /** Combined snapshot the Studio page polls: both task states + context. */
    public Map<String, Object> getStatus(User user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("digest", status.get(user.getId(), TaskType.DIGEST));
        out.put("email", status.get(user.getId(), TaskType.EMAIL));
        out.put("overview", getOverview(user));
        return out;
    }

    private Map<String, Object> getOverview(User user) {
        UUID userId = user.getId();

        UserPreferences prefs = userPreferencesPort.findByUserId(userId).orElse(null);
        List<String> topics = (prefs != null && prefs.getTopics() != null)
                ? Arrays.asList(prefs.getTopics()) : List.of();

        Digest latest = digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)
                .stream().findFirst().orElse(null);

        Map<String, Object> latestMap = null;
        if (latest != null) {
            latestMap = new LinkedHashMap<>();
            latestMap.put("id", latest.getId().toString());
            latestMap.put("generatedAt", latest.getGeneratedAt() != null
                    ? latest.getGeneratedAt().toString() : null);
            latestMap.put("emailSentAt", latest.getEmailSentAt() != null
                    ? latest.getEmailSentAt().toString() : null);
        }

        boolean hasUnsent = digestPort
                .findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(userId)
                .isPresent();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("email", user.getEmail());
        overview.put("topics", topics);
        overview.put("topicCount", topics.size());
        overview.put("latestDigest", latestMap);
        overview.put("hasUnsentDigest", hasUnsent);
        return overview;
    }
}
