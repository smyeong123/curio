package com.curio.news.service;

import com.curio.news.port.out.AiService;
import com.curio.news.dto.DigestResponse;
import com.curio.news.dto.NewsSummary;
import com.curio.news.entity.Digest;
import com.curio.news.port.in.DigestGeneration;
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
import com.curio.shared.time.DigestDay;
import com.curio.shared.i18n.Language;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
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
     * Intentionally NOT @Transactional. This orchestrates several AI-provider
     * calls (each up to ~90s); holding a pooled JDBC connection open across them
     * would exhaust the connection pool under the digest job's concurrency. The DB
     * touches here (existence check, preferences load, final save) each run in
     * their own short transaction via the repository layer.
     */
    @Override
    public DigestGeneration generate(User user, DigestProgressListener progressListener) {
        DigestProgressListener listener = (progressListener != null)
                ? progressListener : DigestProgressListener.NOOP;

        // One digest per user per digest day (05:00 KST → 05:00 KST), matching the
        // V29 unique index; generatedAt is stored in UTC, so compare in UTC.
        LocalDate day = DigestDay.current(Clock.systemUTC());
        if (digestPort.existsByUserIdAndGeneratedAtBetween(
                user.getId(), DigestDay.startUtc(day), DigestDay.endUtc(day))) {
            log.info("Digest already exists for user {} for digest day {}, skipping", user.getId(), day);
            return DigestGeneration.alreadyExists();
        }

        UserPreferences preferences = userPreferencesPort.findByUserId(user.getId())
                .orElse(null);

        if (preferences == null || preferences.getTopics().length == 0) {
            log.warn("User {} has no preferences set, skipping digest generation", user.getId());
            return DigestGeneration.noTopics();
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
                    // Older cached entries have no importance; stamp the neutral default.
                    for (NewsSummary summary : summaries) {
                        summary.setTopic(topic);
                        summary.setImportance(NewsSummary.importanceOf(summary.getImportance()));
                    }
                    perTopic.add(new ArrayList<>(summaries));
                }
            } catch (Exception e) {
                log.error("Failed to generate summaries for topic: {} for user: {}", topic, user.getId(), e);
            }
        }

        SeenStories seen = recentlySentStories(user, day);
        List<NewsSummary> allSummaries = pickStories(perTopic, seen);

        if (allSummaries.isEmpty()) {
            log.warn("No summaries generated for user {}", user.getId());
            return DigestGeneration.nothingGenerated();
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
            return DigestGeneration.generated(digestPort.save(digest));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Lost a race with a concurrent run for this user/day (the
            // one-digest-per-user-per-UTC-day unique index, V23). Treat as
            // already-generated rather than a failure.
            log.info("Digest for user {} was generated concurrently, skipping", user.getId());
            return DigestGeneration.alreadyExists();
        }
    }

    /**
     * Chooses and orders the digest's stories, capped at {@link #MAX_SUMMARIES_PER_DIGEST}.
     * Every story is ranked by importance (high first), then by its position in its topic's
     * list (the model's own ranking), then by topic order. Walking that ranking:
     * <ol>
     *   <li>fairness pass — each topic's first story in the ranking that isn't a repeat;</li>
     *   <li>fill pass — the remaining non-repeats, in ranking order, until the cap.</li>
     * </ol>
     * A repeat is a story already picked or in this reader's recent digests ({@code seen}) —
     * by article URL (its own or an "also covered by" one) or, told by another outlet, by
     * {@link StorySimilarity}; it is skipped and the topic's next story stands in. The result is sorted by importance (stable), so equal-importance stories
     * keep pick order: topic leads first, then the fill. With equal ratings this is
     * round-robin across topics. The same capped list backs the email, web view and quiz.
     */
    static List<NewsSummary> pickStories(List<List<NewsSummary>> perTopic, SeenStories seen) {
        record Ranked(NewsSummary story, int importance, int position, int topic) {}
        List<Ranked> ranking = new ArrayList<>();
        for (int t = 0; t < perTopic.size(); t++) {
            List<NewsSummary> bucket = perTopic.get(t);
            for (int p = 0; p < bucket.size(); p++) {
                NewsSummary s = bucket.get(p);
                ranking.add(new Ranked(s, NewsSummary.importanceOf(s.getImportance()), p, t));
            }
        }
        ranking.sort(Comparator.comparingInt(Ranked::importance).reversed()
                .thenComparingInt(Ranked::position)
                .thenComparingInt(Ranked::topic));

        List<Ranked> picked = new ArrayList<>();
        Set<Integer> topicsWithAPick = new HashSet<>();
        Set<Ranked> taken = Collections.newSetFromMap(new IdentityHashMap<>());
        // Fairness pass: one story per topic, the highest-ranked one that isn't a repeat.
        for (Ranked r : ranking) {
            if (picked.size() >= MAX_SUMMARIES_PER_DIGEST) break;
            if (topicsWithAPick.contains(r.topic())) continue;
            if (seen.claim(r.story())) {
                picked.add(r);
                taken.add(r);
                topicsWithAPick.add(r.topic());
            }
        }
        // Fill pass: everything else by rank until the cap.
        for (Ranked r : ranking) {
            if (picked.size() >= MAX_SUMMARIES_PER_DIGEST) break;
            if (taken.contains(r)) continue;
            if (seen.claim(r.story())) {
                picked.add(r);
            }
        }
        // List.sort is stable: equal importance keeps pick order.
        picked.sort(Comparator.comparingInt(Ranked::importance).reversed());
        return picked.stream().map(Ranked::story).toList();
    }

    /** How many earlier digest days a story counts as already sent to a reader. */
    private static final int REPEAT_LOOKBACK_DAYS = 14;

    /**
     * The stories in this reader's digests from the previous {@link #REPEAT_LOOKBACK_DAYS}
     * digest days. A lookup failure means nothing is filtered rather than no digest.
     */
    private SeenStories recentlySentStories(User user, LocalDate day) {
        SeenStories seen = new SeenStories();
        try {
            List<Digest> recent = digestPort.findByUserIdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(
                    user.getId(), DigestDay.startUtc(day.minusDays(REPEAT_LOOKBACK_DAYS)), DigestDay.startUtc(day));
            for (Digest d : recent) {
                Object summaries = d.getContent() == null ? null : d.getContent().get("summaries");
                if (!(summaries instanceof List<?> list)) continue;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) seen.add(storyUrls(m), fingerprint(m));
                }
            }
        } catch (Exception e) {
            log.warn("Could not load recent digests for user {} — not filtering repeats: {}", user.getId(), e.toString());
        }
        return seen;
    }

    private static Set<String> storyUrls(NewsSummary s) {
        Set<String> urls = new HashSet<>();
        addUrl(urls, s.getSourceUrl());
        if (s.getAlsoCoveredBy() != null) {
            s.getAlsoCoveredBy().forEach(link -> addUrl(urls, link.url()));
        }
        return urls;
    }

    /** Same as {@link #storyUrls(NewsSummary)} for a summary stored in a digest's JSON content. */
    private static Set<String> storyUrls(Map<?, ?> stored) {
        Set<String> urls = new HashSet<>();
        if (stored.get("source_url") instanceof String u) addUrl(urls, u);
        if (stored.get("also_covered_by") instanceof List<?> also) {
            for (Object link : also) {
                if (link instanceof Map<?, ?> l && l.get("url") instanceof String u) addUrl(urls, u);
            }
        }
        return urls;
    }

    private static void addUrl(Set<String> urls, String url) {
        if (url != null && !url.isBlank()) urls.add(NewsApiClient.normalizeUrl(url));
    }

    /** The fingerprint of a summary stored in a digest's JSON content. */
    private static StorySimilarity.Fingerprint fingerprint(Map<?, ?> stored) {
        return StorySimilarity.fingerprint(
                stored.get("headline") instanceof String h ? h : null,
                stored.get("summary") instanceof String s ? s : null);
    }

    /**
     * The stories a reader already got (recent digests) or that today's digest already holds.
     * URLs catch the same article; fingerprints catch the same story told by another outlet on
     * a later day, which a URL check alone lets through.
     */
    static final class SeenStories {
        private final Set<String> urls = new HashSet<>();
        private final List<StorySimilarity.Fingerprint> fingerprints = new ArrayList<>();

        void add(Set<String> storyUrls, StorySimilarity.Fingerprint fingerprint) {
            urls.addAll(storyUrls);
            if (!fingerprint.isEmpty()) fingerprints.add(fingerprint);
        }

        /** Records the story and returns true, unless it repeats one already seen. */
        boolean claim(NewsSummary story) {
            Set<String> storyUrls = storyUrls(story);
            for (String u : storyUrls) {
                if (urls.contains(u)) return false;
            }
            StorySimilarity.Fingerprint fingerprint = fingerprintOf(story);
            for (StorySimilarity.Fingerprint seen : fingerprints) {
                if (StorySimilarity.sameStory(fingerprint, seen)) return false;
            }
            add(storyUrls, fingerprint);
            return true;
        }

        /** Fail-open: text the matcher cannot handle makes the story count as new, never as a repeat. */
        private static StorySimilarity.Fingerprint fingerprintOf(NewsSummary story) {
            try {
                return StorySimilarity.fingerprint(story.getHeadline(), story.getSummary());
            } catch (RuntimeException e) {
                log.warn("Could not fingerprint story '{}' — URL check only: {}", story.getHeadline(), e.toString());
                return StorySimilarity.Fingerprint.EMPTY;
            }
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
