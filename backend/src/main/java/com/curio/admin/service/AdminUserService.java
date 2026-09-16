package com.curio.admin.service;

import com.curio.admin.port.in.AdminUserUseCase;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import com.curio.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService implements AdminUserUseCase {

    private final UserPort userPort;
    private final UserPreferencesPort userPreferencesPort;
    private final DigestPort digestPort;
    private final QuizAttemptPort quizAttemptPort;

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getUsers(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users = (search != null && !search.isBlank())
                ? userPort.findByEmailContainingIgnoreCase(search.trim(), pageable)
                : userPort.findAll(pageable);

        List<UUID> userIds = users.getContent().stream().map(User::getId).toList();
        Map<UUID, UserPreferences> prefsMap = userPreferencesPort.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));

        return users.map(user -> {
            String[] topics = prefsMap.containsKey(user.getId())
                    ? prefsMap.get(user.getId()).getTopics()
                    : new String[0];

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", user.getId());
            summary.put("email", user.getEmail());
            summary.put("fullName", user.getFullName());
            summary.put("isAdmin", user.getIsAdmin());
            summary.put("deliveryEnabled", user.getDeliveryEnabled());
            summary.put("emailVerified", user.getEmailVerified());
            summary.put("createdAt", user.getCreatedAt());
            summary.put("topics", Arrays.asList(topics));
            summary.put("topicsCount", topics.length);
            return summary;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserDetail(UUID userId) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String[] topics = userPreferencesPort.findByUserId(user.getId())
                .map(UserPreferences::getTopics)
                .orElse(new String[0]);

        long digestCount = digestPort.countByUserId(userId);
        long quizAttempts = quizAttemptPort.countByUserId(userId);
        Double avgScore = quizAttemptPort.findAverageScoreByUserId(userId);

        List<Map<String, Object>> recentDigests = digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)
                .stream()
                .map(this::toDigestEntry)
                .toList();

        List<Map<String, Object>> recentQuizAttempts = quizAttemptPort.findTop5ByUserIdOrderByCompletedAtDesc(userId)
                .stream()
                .map(this::toQuizAttemptEntry)
                .toList();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("fullName", user.getFullName());
        profile.put("isAdmin", user.getIsAdmin());
        profile.put("deliveryEnabled", user.getDeliveryEnabled());
        profile.put("emailVerified", user.getEmailVerified());
        profile.put("createdAt", user.getCreatedAt());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("digestCount", digestCount);
        metrics.put("quizAttempts", quizAttempts);
        metrics.put("averageQuizScore", avgScore != null ? Math.round(avgScore * 100.0) / 100.0 : null);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("user", profile);
        detail.put("topics", Arrays.asList(topics));
        detail.put("metrics", metrics);
        detail.put("recentDigests", recentDigests);
        detail.put("recentQuizAttempts", recentQuizAttempts);
        return detail;
    }

    private Map<String, Object> toDigestEntry(Digest digest) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", digest.getId());
        entry.put("generatedAt", digest.getGeneratedAt());
        entry.put("emailSentAt", digest.getEmailSentAt());
        return entry;
    }

    private Map<String, Object> toQuizAttemptEntry(QuizAttempt attempt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", attempt.getId());
        entry.put("quizId", attempt.getQuiz().getId());
        entry.put("score", attempt.getScore());
        entry.put("completedAt", attempt.getCompletedAt());
        return entry;
    }
}
