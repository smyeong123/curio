package com.curio.admin.service;

import com.curio.admin.dto.AdminDigestResponse;
import com.curio.admin.port.in.AdminDigestUseCase;
import com.curio.news.entity.Digest;
import com.curio.news.port.in.NewsUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.shared.exception.RootCauses;
import com.curio.shared.scheduler.DigestGenerationJob;
import com.curio.shared.scheduler.JobStatusRegistry;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDigestService implements AdminDigestUseCase {

    private static final int CHUNK_SIZE = 500;

    private final DigestPort digestPort;
    private final UserPort userPort;
    private final UserPreferencesPort userPreferencesPort;
    private final NewsUseCase newsUseCase;
    private final JobStatusRegistry jobStatusRegistry;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminDigestResponse> getDigests(int page, int size, String topic, String userEmail) {
        // All underlying finders already sort by generated_at DESC in their SQL
        // (either via method name or the @Query ORDER BY). Adding a Sort here
        // would get appended to native queries using JPA property names — which
        // don't translate for native SQL and blow up at runtime.
        Pageable pageable = PageRequest.of(page, size);

        Page<Digest> digests;
        boolean hasTopic = topic != null && !topic.isBlank();
        boolean hasEmail = userEmail != null && !userEmail.isBlank();

        if (hasEmail) {
            UUID userId = userPort.findByEmail(userEmail.trim())
                    .map(User::getId)
                    .orElse(null);
            if (userId == null) {
                return Page.empty(pageable);
            }
            digests = hasTopic
                    ? digestPort.findByUserIdAndTopicInContent(userId, topic.trim(), pageable)
                    : digestPort.findByUserIdOrderByGeneratedAtDesc(userId, pageable);
        } else if (hasTopic) {
            digests = digestPort.findByTopicInContent(topic.trim(), pageable);
        } else {
            digests = digestPort.findAllByOrderByGeneratedAtDesc(pageable);
        }

        // Digest.user is LAZY; reading email/fullName per row would fire one
        // SELECT per distinct user in the page (N+1 on broad listings). Accessing
        // the proxy's id is free, so batch-load the owners in one query instead.
        Map<UUID, User> ownersById = new HashMap<>();
        List<UUID> ownerIds = digests.getContent().stream()
                .map(d -> d.getUser().getId())
                .distinct()
                .toList();
        if (!ownerIds.isEmpty()) {
            userPort.findAllByIds(ownerIds).forEach(u -> ownersById.put(u.getId(), u));
        }

        return digests.map(digest -> {
            User owner = ownersById.getOrDefault(digest.getUser().getId(), digest.getUser());
            return AdminDigestResponse.builder()
                    .id(digest.getId())
                    .userId(owner.getId())
                    .userEmail(owner.getEmail())
                    .userFullName(owner.getFullName())
                    .content(digest.getContent())
                    .generatedAt(digest.getGeneratedAt())
                    .emailSentAt(digest.getEmailSentAt())
                    .build();
        });
    }

    @Override
    public Map<String, Object> triggerDigestGeneration() {
        return triggerDigestGeneration(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerDigestGeneration(List<String> topicFilter) {
        int successCount = 0;
        int failCount = 0;

        Set<String> requestedTopics = (topicFilter != null && !topicFilter.isEmpty())
                ? new HashSet<>(topicFilter)
                : null;

        // Surface failure detail to the admin UI: a small sample of per-user errors
        // plus a count-by-exception-class aggregate. The UI renders these under
        // the "Generate Digests" panel so admins don't need to tail the server log.
        List<Map<String, String>> sampleErrors = new ArrayList<>();
        Map<String, Integer> errorsByType = new LinkedHashMap<>();
        int sampleLimit = 3;
        int skippedCount = 0;
        long totalUsers = 0;

        // UTC to match Digest.generatedAt stamping and the V23 unique index's day.
        LocalDateTime startOfDay = LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // Page through delivery-enabled users instead of loading the whole table.
        // Preferences are loaded once per user and reused for both the topic filter
        // and the empty-topics skip check (previously an N+1 across two passes).
        int pageIndex = 0;
        Page<User> page;
        do {
            Pageable pageable = PageRequest.of(pageIndex, CHUNK_SIZE, Sort.by("id"));
            page = userPort.findByDeliveryEnabledTrue(pageable);
            for (User user : page.getContent()) {
                Optional<UserPreferences> prefs = userPreferencesPort.findByUserId(user.getId());

                // Topic filter: users not subscribed to any requested topic are
                // excluded entirely (not counted), matching the prior behavior.
                if (requestedTopics != null) {
                    if (prefs.isEmpty() || Arrays.stream(prefs.get().getTopics())
                            .noneMatch(requestedTopics::contains)) {
                        continue;
                    }
                }

                totalUsers++;

                // Pre-check skip conditions so they don't get reported as failures.
                if (digestPort.existsByUserIdAndGeneratedAtBetween(user.getId(), startOfDay, endOfDay)) {
                    skippedCount++;
                    continue;
                }
                if (prefs.isEmpty() || prefs.get().getTopics().length == 0) {
                    skippedCount++;
                    continue;
                }

                try {
                    Digest digest = newsUseCase.generateDigestForUser(user);
                    if (digest != null) {
                        successCount++;
                    } else {
                        failCount++;
                        recordError(sampleErrors, errorsByType, sampleLimit, user,
                                "no digest returned — AI provider call failed or circuit open (check " +
                                        "CLAUDE_API_KEY / AI_PROVIDER env and /tmp/curio-backend.log for provider error)");
                    }
                } catch (Exception e) {
                    failCount++;
                    String message = RootCauses.describe(e);
                    log.warn("Admin-triggered digest generation failed for user {}: {}", user.getId(), message);
                    recordError(sampleErrors, errorsByType, sampleLimit, user, message);
                }
            }
            pageIndex++;
        } while (page.hasNext());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        if (skippedCount > 0) {
            result.put("skippedCount", skippedCount);
        }
        if (!sampleErrors.isEmpty()) {
            result.put("sampleErrors", sampleErrors);
        }
        if (!errorsByType.isEmpty()) {
            result.put("errorsByType", errorsByType);
        }
        if (topicFilter != null && !topicFilter.isEmpty()) {
            result.put("topicFilter", topicFilter);
        }
        jobStatusRegistry.recordSuccess(DigestGenerationJob.JOB_NAME, result);
        return result;
    }

    private void recordError(List<Map<String, String>> sampleErrors,
                             Map<String, Integer> errorsByType,
                             int sampleLimit,
                             User user,
                             String message) {
        String typeKey = message.split(":", 2)[0].trim();
        errorsByType.merge(typeKey, 1, Integer::sum);
        if (sampleErrors.size() < sampleLimit) {
            Map<String, String> sample = new LinkedHashMap<>();
            sample.put("userEmail", user.getEmail());
            sample.put("message", message);
            sampleErrors.add(sample);
        }
    }
}
