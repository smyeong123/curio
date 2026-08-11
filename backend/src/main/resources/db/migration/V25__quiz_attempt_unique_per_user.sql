-- Enforce one quiz attempt per (user, quiz): a "better score wins" strategy.
-- Re-taking a quiz must not create a duplicate attempt; only a strictly higher
-- score replaces the stored one (see QuizService.submitQuiz). This migration
-- collapses any pre-existing duplicates (keeping the best score) and adds the
-- unique constraint that guarantees distinctness at the database level.

-- 1. De-duplicate existing rows, keeping the best score per (user, quiz).
--    Ties broken by most-recent completed_at, then smallest id (deterministic).
DELETE FROM quiz_attempts qa
USING quiz_attempts keep
WHERE qa.user_id = keep.user_id
  AND qa.quiz_id = keep.quiz_id
  AND qa.id <> keep.id
  AND (
        keep.score > qa.score
        OR (keep.score = qa.score AND keep.completed_at > qa.completed_at)
        OR (keep.score = qa.score AND keep.completed_at = qa.completed_at AND keep.id < qa.id)
      );

-- 2. Guarantee uniqueness going forward.
ALTER TABLE quiz_attempts
    ADD CONSTRAINT uq_quiz_attempts_user_quiz UNIQUE (user_id, quiz_id);
