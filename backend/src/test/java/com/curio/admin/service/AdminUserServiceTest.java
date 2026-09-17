package com.curio.admin.service;

import com.curio.admin.dto.AdminUserDetail;
import com.curio.admin.dto.AdminUserSummary;
import com.curio.news.entity.Digest;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.entity.Quiz;
import com.curio.quiz.entity.QuizAttempt;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.shared.exception.ResourceNotFoundException;
import com.curio.user.entity.User;
import com.curio.user.entity.UserPreferences;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private DigestPort digestPort;
    @Mock private QuizAttemptPort quizAttemptPort;

    @InjectMocks private AdminUserService adminUserService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("reader@example.com")
                .fullName("Reader")
                .passwordHash("hash")
                .createdAt(LocalDateTime.of(2026, 7, 1, 8, 0))
                .build();
    }

    // --- getUsers ---

    @Test
    void getUsers_countsTopicsFromTheReadersPreferences_andZeroWhenNoneSaved() {
        User newcomer = User.builder().id(UUID.randomUUID()).email("new@example.com").build();
        when(userPort.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user, newcomer), PageRequest.of(0, 20), 2));
        when(userPreferencesPort.findByUserIdIn(List.of(userId, newcomer.getId())))
                .thenReturn(List.of(UserPreferences.builder().user(user)
                        .topics(new String[]{"DeepSeek", "Claude (Anthropic)"}).build()));

        Page<AdminUserSummary> page = adminUserService.getUsers(0, 20, "");

        AdminUserSummary first = page.getContent().get(0);
        assertThat(first.id()).isEqualTo(userId);
        assertThat(first.email()).isEqualTo("reader@example.com");
        assertThat(first.fullName()).isEqualTo("Reader");
        assertThat(first.topicsCount()).isEqualTo(2);
        assertThat(first.deliveryEnabled()).isTrue();
        assertThat(first.createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 8, 0));
        assertThat(page.getContent().get(1).topicsCount()).isZero();
    }

    @Test
    void getUsers_searchesByEmail_whenASearchTermIsGiven() {
        when(userPort.findByEmailContainingIgnoreCase(eq("reader"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));
        when(userPreferencesPort.findByUserIdIn(List.of(userId))).thenReturn(List.of());

        Page<AdminUserSummary> page = adminUserService.getUsers(0, 20, "  reader ");

        assertThat(page.getContent()).extracting(AdminUserSummary::email).containsExactly("reader@example.com");
    }

    // --- getUserDetail ---

    @Test
    void getUserDetail_assemblesProfileTopicsMetricsAndRecentActivity() {
        when(userPort.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.of(
                UserPreferences.builder().user(user).topics(new String[]{"DeepSeek"}).build()));
        when(digestPort.countByUserId(userId)).thenReturn(12L);
        when(quizAttemptPort.countByUserId(userId)).thenReturn(4L);
        when(quizAttemptPort.findAverageScoreByUserId(userId)).thenReturn(3.4567);

        Digest digest = Digest.builder().id(UUID.randomUUID()).user(user)
                .generatedAt(LocalDateTime.of(2026, 7, 3, 6, 0))
                .emailSentAt(LocalDateTime.of(2026, 7, 3, 8, 0)).build();
        when(digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)).thenReturn(List.of(digest));

        Quiz quiz = Quiz.builder().id(UUID.randomUUID()).digest(digest).build();
        QuizAttempt attempt = QuizAttempt.builder().id(UUID.randomUUID()).quiz(quiz).user(user)
                .score(4).completedAt(LocalDateTime.of(2026, 7, 3, 9, 30)).build();
        when(quizAttemptPort.findTop5ByUserIdOrderByCompletedAtDesc(userId)).thenReturn(List.of(attempt));

        AdminUserDetail detail = adminUserService.getUserDetail(userId);

        assertThat(detail.user().getId()).isEqualTo(userId);
        assertThat(detail.user().getEmail()).isEqualTo("reader@example.com");
        assertThat(detail.user().getIsAdmin()).isFalse();
        assertThat(detail.user().getDeliveryEnabled()).isTrue();
        assertThat(detail.user().getEmailVerified()).isFalse();
        assertThat(detail.user().getHasPassword()).isTrue();
        assertThat(detail.topics()).containsExactly("DeepSeek");
        assertThat(detail.metrics().digestCount()).isEqualTo(12L);
        assertThat(detail.metrics().quizAttempts()).isEqualTo(4L);
        assertThat(detail.metrics().averageQuizScore()).isEqualTo(3.46);
        assertThat(detail.recentDigests()).hasSize(1);
        assertThat(detail.recentDigests().get(0).id()).isEqualTo(digest.getId());
        assertThat(detail.recentDigests().get(0).generatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 3, 6, 0));
        assertThat(detail.recentQuizAttempts()).hasSize(1);
        assertThat(detail.recentQuizAttempts().get(0).id()).isEqualTo(attempt.getId());
        assertThat(detail.recentQuizAttempts().get(0).score()).isEqualTo(4);
        assertThat(detail.recentQuizAttempts().get(0).completedAt()).isEqualTo(LocalDateTime.of(2026, 7, 3, 9, 30));
    }

    @Test
    void getUserDetail_leavesAverageScoreNull_untilTheReaderHasTakenAQuiz() {
        when(userPort.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferencesPort.findByUserId(userId)).thenReturn(Optional.empty());
        when(digestPort.countByUserId(userId)).thenReturn(0L);
        when(quizAttemptPort.countByUserId(userId)).thenReturn(0L);
        when(quizAttemptPort.findAverageScoreByUserId(userId)).thenReturn(null);
        when(digestPort.findTop5ByUserIdOrderByGeneratedAtDesc(userId)).thenReturn(List.of());
        when(quizAttemptPort.findTop5ByUserIdOrderByCompletedAtDesc(userId)).thenReturn(List.of());

        AdminUserDetail detail = adminUserService.getUserDetail(userId);

        assertThat(detail.topics()).isEmpty();
        assertThat(detail.metrics().averageQuizScore()).isNull();
        assertThat(detail.recentDigests()).isEmpty();
        assertThat(detail.recentQuizAttempts()).isEmpty();
    }

    @Test
    void getUserDetail_throwsNotFound_forAnUnknownUser() {
        when(userPort.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserDetail(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
