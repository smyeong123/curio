package com.curio.shared.scheduler;

import com.curio.news.entity.Digest;
import com.curio.news.port.in.NewsUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.in.QuizUseCase;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import com.curio.shared.port.in.EmailUseCase;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class EmailSendJob {

    public static final String JOB_NAME = "email-send";
    private static final int CHUNK_SIZE = 500;

    private final UserPort userPort;
    private final DigestPort digestPort;
    private final EmailUseCase emailService;
    private final NewsUseCase newsService;
    private final QuizUseCase quizService;
    private final UserPreferencesPort preferencesPort;
    private final JobStatusRegistry jobStatusRegistry;
    private final Executor emailExecutor;

    /**
     * Fallback timezone for users who have no timezone set yet (e.g. registered before
     * timezone capture, or never opened the app). Defaults to UTC — set
     * APP_DEFAULT_DELIVERY_TIMEZONE (e.g. "Asia/Seoul") to give unknown-location users
     * a sensible local 08:00 instead of 08:00 UTC. Users with a captured timezone are
     * unaffected.
     */
    @Value("${app.default-delivery-timezone:UTC}")
    private String defaultDeliveryTimezone;

    public EmailSendJob(
            UserPort userPort,
            DigestPort digestPort,
            EmailUseCase emailService,
            NewsUseCase newsService,
            QuizUseCase quizService,
            UserPreferencesPort preferencesPort,
            JobStatusRegistry jobStatusRegistry,
            @Qualifier("emailExecutor") Executor emailExecutor) {
        this.userPort = userPort;
        this.digestPort = digestPort;
        this.emailService = emailService;
        this.newsService = newsService;
        this.quizService = quizService;
        this.preferencesPort = preferencesPort;
        this.jobStatusRegistry = jobStatusRegistry;
        this.emailExecutor = emailExecutor;
    }

    /**
     * Runs daily at 8:00 AM UTC to send digest emails to all users
     * who have delivery enabled and have an unsent digest for today.
     * Uses a thread pool for parallel email sending.
     */
    // Runs hourly at :00 UTC. Per-user filtering by `preferences.deliveryHour`
    // (interpreted in `preferences.timezone`) gates the actual send. Users with
    // no preference default to 8:00 in their local timezone (UTC if unset).
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @SchedulerLock(name = "email-send", lockAtLeastFor = "PT5M", lockAtMostFor = "PT1H")
    public void sendDailyEmails() {
        log.info("Starting daily email send job...");
        long start = System.currentTimeMillis();

        AtomicInteger sentCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        int pageIndex = 0;
        long totalProcessed = 0;
        Page<User> page;

        // Page through delivery-enabled users instead of loading everyone at once —
        // prevents OOM and lets each chunk's futures be GC'd before the next loads.
        do {
            Pageable pageable = PageRequest.of(pageIndex, CHUNK_SIZE, Sort.by("id"));
            page = userPort.findByDeliveryEnabledTrue(pageable);
            List<User> chunk = page.getContent();
            if (chunk.isEmpty()) {
                break;
            }

            log.info("Email chunk {} ({} users, total so far {})", pageIndex, chunk.size(), totalProcessed);

            // Batch-load preferences for this chunk to avoid N+1 lookups.
            Map<java.util.UUID, UserPreferences> prefsByUserId = preferencesPort
                    .findByUserIdIn(chunk.stream().map(User::getId).collect(Collectors.toList()))
                    .stream()
                    .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p, (a, b) -> a, HashMap::new));

            List<CompletableFuture<Void>> futures = new ArrayList<>(chunk.size());
            for (User user : chunk) {
                UserPreferences prefs = prefsByUserId.get(user.getId());
                if (!isDeliveryHourForUser(prefs)) {
                    continue;
                }
                // orTimeout doesn't cancel the running task, so both the timeout handler
                // and the task's own completion path can reach the counters for the same
                // user. First completion wins the count; the loser only logs.
                AtomicBoolean counted = new AtomicBoolean(false);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        Optional<Digest> unsentDigest = digestPort
                                .findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(user.getId());

                        // Delivery hours that fall before the 06:00 UTC generation job (e.g. early
                        // hours in near-UTC zones) would otherwise ship yesterday's digest. If the
                        // newest unsent digest isn't from today, generate one just-in-time before
                        // sending. generateDigestForUser is idempotent per (user, topic, date) and
                        // cache/SingleFlight-backed, so this is cheap and safe.
                        if (unsentDigest.isEmpty() || !isFromToday(unsentDigest.get())) {
                            try {
                                Digest fresh = newsService.generateDigestForUser(user);
                                if (fresh != null) {
                                    try {
                                        quizService.generateQuizForDigest(fresh);
                                    } catch (Exception qe) {
                                        log.warn("Just-in-time quiz generation failed for digest {}",
                                                fresh.getId(), qe);
                                    }
                                }
                            } catch (Exception ge) {
                                log.error("Just-in-time digest generation failed for user {}",
                                        user.getId(), ge);
                            }
                            // Re-select the newest unsent digest, now including anything just generated.
                            unsentDigest = digestPort
                                    .findFirstByUserIdAndEmailSentAtIsNullOrderByGeneratedAtDesc(user.getId());
                        }

                        // Only ever send *today's* digest. If generation above failed
                        // or produced nothing, the newest unsent digest could be a
                        // multi-day-old one; sending it would mislabel a stale digest as
                        // "today's". Skip rather than send stale — the next hourly tick
                        // (catch-up gate) or tomorrow's run retries.
                        if (unsentDigest.isEmpty() || !isFromToday(unsentDigest.get())) return;

                        EmailUseCase.DigestSendOutcome outcome =
                                emailService.sendDigestEmail(user, unsentDigest.get());
                        if (outcome == EmailUseCase.DigestSendOutcome.SENT) {
                            if (counted.compareAndSet(false, true)) {
                                sentCount.incrementAndGet();
                            } else {
                                log.warn("Email for user {} was sent but had already been counted as timed out", user.getId());
                            }
                        } else {
                            // Claim lost to a concurrent sender — not this job's send, so
                            // it counts as neither sent nor failed here.
                            counted.compareAndSet(false, true);
                        }
                    } catch (Exception e) {
                        if (counted.compareAndSet(false, true)) {
                            failCount.incrementAndGet();
                        }
                        log.error("Failed to send email for user {}", user.getId(), e);
                    }
                }, emailExecutor)
                        // 10 min to match DigestGenerationJob: the just-in-time generation
                        // branch runs the same sequential per-topic AI calls, so 5 min could
                        // time out a normal cold-cache send for a multi-topic user.
                        .orTimeout(10, TimeUnit.MINUTES)
                        // orTimeout completes the future outside the lambda's try/catch —
                        // without this, a timed-out user is dropped silently and counted nowhere.
                        .exceptionally(ex -> {
                            if (counted.compareAndSet(false, true)) {
                                failCount.incrementAndGet();
                                log.error("Email task timed out or failed for user {}", user.getId(), ex);
                            }
                            return null;
                        }));
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Email chunk {} timed out or was interrupted", pageIndex, e);
            }

            totalProcessed += chunk.size();
            pageIndex++;
        } while (page.hasNext());

        long duration = System.currentTimeMillis() - start;
        log.info("Email send job completed in {}ms across {} users in {} chunks: {} sent, {} failed",
                duration, totalProcessed, pageIndex, sentCount.get(), failCount.get());
        jobStatusRegistry.recordSuccess(JOB_NAME, Map.of(
                "sentCount", sentCount.get(),
                "failCount", failCount.get(),
                "totalUsersProcessed", totalProcessed,
                "chunks", pageIndex,
                "chunkSize", CHUNK_SIZE,
                "durationMs", duration
        ));
    }

    /**
     * True when the current hour in the user's timezone is at or past their preferred
     * delivery hour (catch-up semantics). Defaults: 8:00 in their TZ; UTC when TZ is unset.
     *
     * <p>Uses {@code >=} rather than exact equality so a DST spring-forward that skips
     * the target hour (e.g. clocks jump 01:59→03:00, skipping 02:00) still delivers on
     * the next hourly tick instead of silently dropping that day's email. Repeated
     * post-target ticks don't double-send: {@code claimForEmailSend} stamps
     * {@code emailSentAt}, so the "newest unsent digest" lookup returns empty once sent.
     */
    private boolean isDeliveryHourForUser(UserPreferences prefs) {
        Integer prefHour = (prefs != null) ? prefs.getDeliveryHour() : null;
        String tz = (prefs != null) ? prefs.getTimezone() : null;
        int targetHour = (prefHour != null) ? prefHour : 8;
        ZoneId zone;
        try {
            zone = (tz != null && !tz.isBlank()) ? ZoneId.of(tz) : defaultZone();
        } catch (Exception e) {
            zone = defaultZone();
        }
        return ZonedDateTime.now(zone).getHour() >= targetHour;
    }

    /** Configured fallback zone for users with no timezone; UTC if misconfigured. */
    private ZoneId defaultZone() {
        try {
            return ZoneId.of(defaultDeliveryTimezone);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    /**
     * True when the digest was generated on the current UTC calendar day — UTC to
     * match how {@code Digest.generatedAt} is stamped and how the V23 unique index
     * defines "day". If it errs (treating a fresh digest as stale) the only cost is
     * an idempotent, cache-backed regeneration — never a stale send.
     */
    private boolean isFromToday(Digest digest) {
        return digest != null && digest.getGeneratedAt() != null
                && digest.getGeneratedAt().toLocalDate().equals(LocalDate.now(java.time.ZoneOffset.UTC));
    }
}
