package com.curio.quiz.repository;

import com.curio.quiz.entity.QuizAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    // id DESC tiebreaker: completedAt alone isn't unique (retakes/batch inserts can
    // share a timestamp), and a non-deterministic order makes paginated history skip
    // or repeat rows across pages.
    @Query("SELECT qa FROM QuizAttempt qa JOIN FETCH qa.quiz WHERE qa.user.id = :userId ORDER BY qa.completedAt DESC, qa.id DESC")
    Page<QuizAttempt> findByUserIdWithQuiz(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT qa FROM QuizAttempt qa WHERE qa.user.id = :userId AND qa.quiz.id = :quizId")
    Optional<QuizAttempt> findByUserIdAndQuizId(@Param("userId") UUID userId, @Param("quizId") UUID quizId);

    // SELECT ... FOR UPDATE: serializes concurrent "better score wins" retakes on
    // the same attempt row so a lower score committed last can't clobber a higher
    // one (unguarded read-modify-write would be last-write-wins).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT qa FROM QuizAttempt qa WHERE qa.user.id = :userId AND qa.quiz.id = :quizId")
    Optional<QuizAttempt> findByUserIdAndQuizIdForUpdate(@Param("userId") UUID userId, @Param("quizId") UUID quizId);

    Page<QuizAttempt> findByUserIdOrderByCompletedAtDesc(UUID userId, Pageable pageable);
    List<QuizAttempt> findTop5ByUserIdOrderByCompletedAtDesc(UUID userId);
    long countByUserId(UUID userId);
    long countByCompletedAtAfter(java.time.LocalDateTime dateTime);

    @Query("select avg(qa.score) from QuizAttempt qa where qa.user.id = :userId")
    Double findAverageScoreByUserId(@Param("userId") UUID userId);
}
