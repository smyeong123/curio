package com.curio.shared.digest;

import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.batch.SubscriberBatch;
import com.curio.shared.batch.SubscriberBatch.Outcome;
import com.curio.shared.batch.SubscriberBatch.Spec;
import com.curio.shared.batch.SubscriberBatch.Tally;
import com.curio.shared.exception.RootCauses;
import com.curio.shared.jobs.JobRunRecorder;
import com.curio.shared.port.in.EmailUseCase;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPreferencesPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Emails digests to subscribers. Two modes share one loop:
 * <ul>
 *   <li>{@link #sendDue()} — the hourly run: only users whose local delivery hour
 *       has arrived, only today's digest, generated just-in-time when missing.</li>
 *   <li>{@link #sendAllUnsent()} — the admin "Send now": every subscriber with any
 *       unsent digest, no hour gate, nothing generated.</li>
 * </ul>
 * Both record the same result shape under {@link #JOB_NAME}, and a run that dies
 * is recorded as such by the batch itself.
 */
@Component
@Slf4j
public class DigestEmailBatch {

    public static final String JOB_NAME = "email-send";

    private final SubscriberBatch subscriberBatch;
    private final DigestPort digestPort;
    private final EmailUseCase emailService;
    private final DigestPipeline pipeline;
    private final UserPreferencesPort preferencesPort;
    private final JobRunRecorder recorder;
    private final ThreadPoolTaskExecutor emailExecutor;
    private final Clock clock;

    /**
     * Fallback timezone for users who have no timezone set (registered before
     * timezone capture, or never opened the app). Set APP_DEFAULT_DELIVERY_TIMEZONE
     * (e.g. "Asia/Seoul") to give unknown-location users a sensible local 08:00
     * instead of 08:00 UTC.
     */
    private final String defaultDeliveryTimezone;

    public DigestEmailBatch(SubscriberBatch subscriberBatch,
                            DigestPort digestPort,
                            EmailUseCase emailService,
                            DigestPipeline pipeline,
                            UserPreferencesPort preferencesPort,
                            JobRunRecorder recorder,
                            @Qualifier("emailExecutor") ThreadPoolTaskExecutor emailExecutor,
                            Clock clock,
                            @Value("${app.default-delivery-timezone:UTC}") String defaultDeliveryTimezone) {
        this.subscriberBatch = subscriberBatch;
        this.digestPort = digestPort;
        this.emailService = emailService;
        this.pipeline = pipeline;
        this.preferencesPort = preferencesPort;
        this.recorder = recorder;
        this.emailExecutor = emailExecutor;
        this.clock = clock;
        this.defaultDeliveryTimezone = defaultDeliveryTimezone;
    }

    public Map<String, Object> sendDue() {
        return run(true);
    }

    public Map<String, Object> sendAllUnsent() {
        return run(false);
    }

    private Map<String, Object> run(boolean scheduled) {
        long start = System.currentTimeMillis();
        try {
            return deliver(scheduled, start);
        } catch (RuntimeException e) {
            recorder.recordFailure(JOB_NAME, e);
            throw e;
        }
    }

    private Map<String, Object> deliver(boolean scheduled, long start) {
        // Preferences are batch-loaded per chunk (one query, not one per user) —
        // the hour gate needs every user's timezone and delivery hour.
        ConcurrentHashMap<UUID, UserPreferences> prefsByUser = new ConcurrentHashMap<>();
        Consumer<List<User>> preload = scheduled
                ? chunk -> preferencesPort.findByUserIdIn(chunk.stream().map(User::getId).toList())
                        .forEach(prefs -> prefsByUser.put(prefs.getUser().getId(), prefs))
                : null;
        Predicate<User> include = scheduled
                ? user -> isDeliveryHourFor(prefsByUser.get(user.getId()))
                : user -> true;

        // 10 min per user: the just-in-time branch runs the same sequential AI
        // calls as digest generation, so 5 min could time out a normal cold send.
        Spec spec = Spec.forPool(JOB_NAME, emailExecutor, Duration.ofMinutes(10), 5);
        Tally tally = subscriberBatch.run(spec, preload, include,
                scheduled ? this::deliverTodaysDigest : this::deliverAnyUnsentDigest);

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("sentCount", tally.success());
        extras.put("failCount", tally.failed());
        return recorder.recordRun(JOB_NAME, tally, System.currentTimeMillis() - start, extras);
    }

    /**
     * Scheduled delivery: only ever send TODAY's digest. Delivery hours before the
     * 06:00 UTC generation run would otherwise ship yesterday's edition, so a
     * missing today's digest is generated just-in-time (idempotent per user/day
     * and cache-backed, so cheap). A generation that throws is this user's
     * failure; one that quietly yields nothing is a skip rather than mislabelling
     * a stale digest as today's — the next hourly tick retries either way.
     *
     * <p>One query answers the common case: every tick after a user's delivery
     * hour finds today's digest already sent and stops there.
     */
    private Outcome deliverTodaysDigest(User user) {
        Optional<Digest> newest = digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId());
        if (newest.isEmpty() || !isFromToday(newest.get())) {
            try {
                pipeline.generateWithQuiz(user);
            } catch (Exception e) {
                log.error("Just-in-time digest generation failed for user {}", user.getId(), e);
                return Outcome.failed("generate: " + RootCauses.describe(e));
            }
            newest = digestPort.findFirstByUserIdOrderByGeneratedAtDesc(user.getId());
        }
        if (newest.isEmpty() || !isFromToday(newest.get()) || newest.get().getEmailSentAt() != null) {
            return Outcome.skipped();
        }
        return send(user, newest.get());
    }

    /** Admin delivery: whatever is unsent, right now. */
    private Outcome deliverAnyUnsentDigest(User user) {
        return digestPort.findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(user.getId())
                .map(digest -> send(user, digest))
                .orElse(Outcome.skipped());
    }

    /** A lost claim means another path (Studio, the other trigger) owns this send — neither ours nor a failure. */
    private Outcome send(User user, Digest digest) {
        return emailService.sendDigestEmail(user, digest) == EmailUseCase.DigestSendOutcome.SENT
                ? Outcome.success()
                : Outcome.skipped();
    }

    /**
     * True when the current hour in the user's timezone is at or past their
     * delivery hour (catch-up semantics). Defaults: 8:00 in their zone; the
     * configured fallback zone when unset.
     *
     * <p>{@code >=} rather than equality so a DST spring-forward that skips the
     * target hour still delivers on the next hourly tick. Repeated post-target
     * ticks don't double-send: the send claim stamps {@code emailSentAt}, so the
     * "newest unsent digest" lookup comes back empty once sent.
     */
    boolean isDeliveryHourFor(UserPreferences prefs) {
        Integer prefHour = (prefs != null) ? prefs.getDeliveryHour() : null;
        String tz = (prefs != null) ? prefs.getTimezone() : null;
        int targetHour = (prefHour != null) ? prefHour : 8;
        ZoneId zone;
        try {
            zone = (tz != null && !tz.isBlank()) ? ZoneId.of(tz) : defaultZone();
        } catch (Exception e) {
            zone = defaultZone();
        }
        return ZonedDateTime.now(clock.withZone(zone)).getHour() >= targetHour;
    }

    private ZoneId defaultZone() {
        try {
            return ZoneId.of(defaultDeliveryTimezone);
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    /**
     * Generated on the current UTC calendar day — UTC to match how
     * {@code Digest.generatedAt} is stamped and how the V23 unique index defines "day".
     */
    boolean isFromToday(Digest digest) {
        return digest != null && digest.getGeneratedAt() != null
                && digest.getGeneratedAt().toLocalDate().equals(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
    }
}
