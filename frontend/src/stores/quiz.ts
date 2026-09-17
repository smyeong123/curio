import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api'
import type { QuizAttempt, QuizSubmitResponse } from '@/types/quiz'

// Hard cap on history pages, so a bad page count can never fan out unbounded.
const MAX_HISTORY_PAGES = 100

export const useQuizStore = defineStore('quiz', () => {
  const quizHistory = ref<QuizAttempt[]>([])

  // Monotonic sequence guard: a slow history response must not clobber the
  // store after a newer load (or a logout reset) has resolved.
  let historySeq = 0

  // Quiz GETs can be slow (on-demand generation runs an AI call server-side).
  const fetchQuiz = async (digestId: string) => {
    const response = await api.quiz.getQuiz(digestId)
    return response.data
  }

  const submitQuiz = async (quizId: string, answers: Record<number, string>): Promise<QuizSubmitResponse> => {
    const response = await api.quiz.submitQuiz(quizId, answers)
    return response.data
  }

  // Page through the whole history so headline stats (count, average, best) are
  // computed over ALL attempts, not just the most-recent page. GET /quiz/history
  // is Pageable (10/page): page 0 reveals the page count; the rest are requested
  // together and kept in order.
  const fetchHistory = async () => {
    const seq = ++historySeq
    const first = await api.quiz.getHistory(0)
    const pages = Math.min(first.data.totalPages, MAX_HISTORY_PAGES)
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, pages - 1) }, (_, i) => api.quiz.getHistory(i + 1))
    )
    const all = first.data.content.concat(...rest.map((r) => r.data.content))
    if (seq === historySeq) {
      quizHistory.value = all
    }
    return all
  }

  // Wipe all in-memory state on logout so the next account on this browser
  // never sees a flash of the previous user's quiz history. Bumping the
  // sequence counter also invalidates any response still in flight.
  const reset = () => {
    historySeq++
    quizHistory.value = []
  }

  return {
    quizHistory,
    fetchQuiz,
    submitQuiz,
    fetchHistory,
    reset
  }
})
