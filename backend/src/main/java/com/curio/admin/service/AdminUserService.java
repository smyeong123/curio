package com.curio.admin.service;

import com.curio.admin.dto.AdminUserDetail;
import com.curio.admin.dto.AdminUserDetail.Metrics;
import com.curio.admin.dto.AdminUserDetail.RecentDigest;
import com.curio.admin.dto.AdminUserDetail.RecentQuizAttempt;
import com.curio.admin.dto.AdminUserSummary;
import com.curio.admin.port.in.AdminUserUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.user.dto.UserResponse;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public Page<AdminUserSummary> getUsers(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users = (search != null && !search.isBlank())
                ? userPort.findByEmailContainingIgnoreCase(search.trim(), pageable)
                : userPort.findAll(pageable);

        // One preferences query for the whole page instead of one per row.
        List<UUID> userIds = users.getContent().stream().map(User::getId).toList();
        Map<UUID, UserPreferences> prefsByUser = userPreferencesPort.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));

        return users.map(user -> {
            UserPreferences prefs = prefsByUser.get(user.getId());
            int topicsCount = prefs != null ? prefs.getTopics().length : 0;
            return AdminUserSummary.from(user, topicsCount);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetail getUserDetail(UUID userId) {
        User user = userPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<String> topics = userPreferencesPort.findByUserId(user.getId())
                .map(UserPreferences::getTopics)
                .map(List::of)
                .orElse(List.of());

        Metrics metrics = Metrics.of(
                digestPort.countByUserId(userId),
                quizAttemptPort.countByUserId(userId),
                quizAttemptPort.findAverageScoreByUserId(userId));

        List<RecentDigest> recentDigests = digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)
                .stream()
                .map(RecentDigest::from)
                .toList();

        List<RecentQuizAttempt> recentQuizAttempts = quizAttemptPort.findTop5ByUserIdOrderByCompletedAtDesc(userId)
                .stream()
                .map(RecentQuizAttempt::from)
                .toList();

        return new AdminUserDetail(UserResponse.from(user), topics, metrics, recentDigests, recentQuizAttempts);
    }
}
