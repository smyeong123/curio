package com.curio.news.service;

import com.curio.news.dto.DigestResponse;
import com.curio.news.dto.NewsSummary;
import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestProgressListener;
import com.curio.news.port.in.NewsUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.user.entity.User;
import com.curio.user.entity.UserApiKey;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.in.UserApiKeyUseCase;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.shared.i18n.Language;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService implements NewsUseCase {

    private final DigestPort digestPort;
    private final UserPort userPort;
    private final UserPreferencesPort userPreferencesPort;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    /** Resolves the user's BYOK key (if any) so generation is billed to them. */
    private final UserApiKeyUseCase userApiKeyService;

    @org.springframework.beans.factory.annotation.Value("${ai.provider:claude}")
    private String platformProvider;

    /**
     * Hard cap on stories per digest. Keeps the daily email scannable regardless
     * of how many topics a user selects (each topic yields 2-3 summaries, which
     * would otherwise stack up — e.g. 8 topics → 20+ stories).
     */
    private static final int MAX_SUMMARIES_PER_DIGEST = 8;

    /**
     * Look up the user's BYOK key for the platform's configured provider.
     * Returns null if they have no validated key (→ fall back to platform key).
     * Key never logged. Throws nothing — failures degrade silently to platform key.
     */
    private String resolveUserApiKey(User user) {
        try {
            return userApiKeyService
                    .resolveDecryptedKey(user.getId(), UserApiKey.Provider.fromConfigName(platformProvider))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve BYOK key for user {} — falling back to platform key", user.getId());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<DigestResponse> getDigests(UUID userId, int page, int size) {
        Page<Digest> digests = digestPort.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(page, size));
        return digests.map(this::toDigestResponse);
    }

    @Transactional(readOnly = true)
    public Page<DigestResponse> searchDigests(UUID userId, String query, int page, int size) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) {
            return Page.empty(PageRequest.of(page, size));
        }
        Page<Digest> digests = digestPort.searchUserDigests(userId, q, PageRequest.of(page, size));
        return digests.map(this::toDigestResponse);
    }

    @Transactional(readOnly = true)
    public DigestResponse getDigest(UUID digestId, UUID userId) {
        Digest digest = digestPort.findById(digestId)
                .orElseThrow(() -> new ResourceNotFoundException("Digest not found"));

        if (!digest.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Digest not found");
        }

        return toDigestResponse(digest);
    }

    /**
     * Intentionally NOT @Transactional. This orchestrates several Claude calls
     * (each up to ~90s); holding a pooled JDBC connection open across them would
     * exhaust the connection pool under the digest job's concurrency. The DB
     * touches here (existence check, preferences load, final save) each run in
     * their own short transaction via the repository layer.
     */
    public Digest generateDigestForUser(User user) {
        return generateDigestForUser(user, DigestProgressListener.NOOP);
    }

    public Digest generateDigestForUser(User user, DigestProgressListener progressListener) {
        DigestProgressListener listener = (progressListener != null)
                ? progressListener : DigestProgressListener.NOOP;

        // UTC to match how Digest.generatedAt is stamped and how the V23
        // per-user-per-day unique index defines "day".
        LocalDateTime startOfDay = LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        if (digestPort.existsByUserIdAndGeneratedAtBetween(user.getId(), startOfDay, endOfDay)) {
            log.info("Digest already exists for user {} today, skipping", user.getId());
            return null;
        }

        UserPreferences preferences = userPreferencesPort.findByUserId(user.getId())
                .orElse(null);

        if (preferences == null || preferences.getTopics().length == 0) {
            log.warn("User {} has no preferences set, skipping digest generation", user.getId());
            return null;
        }

        // BYOK: if the user has a validated API key for the platform's
        // configured provider, use it. The key is fetched once and held in
        // a local for the duration of this user's generation, then dropped.
        String userKey = resolveUserApiKey(user);

        // The edition the reader chose (Settings → Edition / onboarding). Stamped into the
        // digest below so the email and quiz for THIS digest follow it even if the user
        // switches editions later.
        Language language = Language.fromCode(preferences.getLanguage());

        // Collect summaries grouped by topic so we can interleave them fairly.
        // A user with many topics would otherwise get a digest dominated by
        // whichever topics happen to be processed first.
        List<List<NewsSummary>> perTopic = new ArrayList<>();

        String[] topics = preferences.getTopics();
        int total = topics.length;
        for (int i = 0; i < total; i++) {
            String topic = topics[i];
            try {
                listener.onTopic(i + 1, total, topic);
                List<NewsSummary> summaries = aiService.generateNewsSummaries(topic, language, userKey);
                if (summaries != null && !summaries.isEmpty()) {
                    // Stamp the canonical topic (the exact beat the user chose in
                    // Settings) onto every summary. The AI is only *asked* to echo
                    // the topic and often paraphrases it (e.g. "Claude" instead of
                    // "Claude (Anthropic)"), which makes the per-story label diverge
                    // from the digest's generatedFor beats and the archive filter.
                    // Normalising here keeps all three in lockstep for every provider.
                    for (NewsSummary summary : summaries) {
                        summary.setTopic(topic);
                    }
                    perTopic.add(new ArrayList<>(summaries));
                }
            } catch (Exception e) {
                log.error("Failed to generate summaries for topic: {} for user: {}", topic, user.getId(), e);
            }
        }

        // Round-robin across topics, then cap the digest so the email stays
        // scannable no matter how many topics the user selected. The same
        // capped list backs the web view and quiz, keeping everything consistent.
        List<NewsSummary> allSummaries = new ArrayList<>();
        for (int round = 0; allSummaries.size() < MAX_SUMMARIES_PER_DIGEST; round++) {
            boolean addedThisRound = false;
            for (List<NewsSummary> bucket : perTopic) {
                if (round < bucket.size()) {
                    allSummaries.add(bucket.get(round));
                    addedThisRound = true;
                    if (allSummaries.size() >= MAX_SUMMARIES_PER_DIGEST) break;
                }
            }
            if (!addedThisRound) break;
        }

        if (allSummaries.isEmpty()) {
            log.warn("No summaries generated for user {}", user.getId());
            return null;
        }

        List<Map<String, Object>> summaryMaps = allSummaries.stream()
                .map(s -> objectMapper.convertValue(s, new TypeReference<Map<String, Object>>() {}))
                .toList();

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("summaries", summaryMaps);
        content.put("generatedFor", Arrays.asList(preferences.getTopics()));
        content.put(Language.CONTENT_KEY, language.code());

        Digest digest = Digest.builder()
                .user(user)
                .content(content)
                .build();

        try {
            return digestPort.save(digest);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Lost a race with a concurrent run for this user/day (the
            // one-digest-per-user-per-UTC-day unique index, V23). Treat as
            // already-generated rather than a failure.
            log.info("Digest for user {} was generated concurrently, skipping", user.getId());
            return null;
        }
    }

    private DigestResponse toDigestResponse(Digest digest) {
        return DigestResponse.builder()
                .id(digest.getId())
                .content(digest.getContent())
                .generatedAt(digest.getGeneratedAt())
                .emailSentAt(digest.getEmailSentAt())
                .build();
    }
}
