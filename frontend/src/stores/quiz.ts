import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api'
import type { QuizAttempt, QuizSubmitResponse } from '@/types/quiz'

export const useQuizStore = defineStore('quiz', () => {
  const currentQuiz = ref<{ id: string; digestId: string; questions: { questions: import('@/types/quiz').QuizQuestion[] } } | null>(null)
  const quizHistory = ref<QuizAttempt[]>([])
  const currentScore = ref<number | null>(null)

  // Monotonic sequence guards: quiz GETs can be slow (on-demand generation runs
  // an AI call server-side), so a stale response from quiz A must not clobber
  // the store after the user has already navigated to quiz B.
  let quizSeq = 0
  let historySeq = 0

  const fetchQuiz = async (digestId: string) => {
    const seq = ++quizSeq
    const response = await api.quiz.getQuiz(digestId)
    if (seq === quizSeq) {
      currentQuiz.value = response.data
      currentScore.value = null
    }
    return response.data
  }

  const submitQuiz = async (quizId: string, answers: Record<number, string>): Promise<QuizSubmitResponse> => {
    const response = await api.quiz.submitQuiz(quizId, answers)
    currentScore.value = response.data.score
    return response.data
  }

  // Page through the whole history so headline stats (count, average, best) are
  // computed over ALL attempts, not just the most-recent page. GET /quiz/history
  // is Pageable (10/page); we accumulate every page.
  const fetchHistory = async () => {
    const seq = ++historySeq
    const all: QuizAttempt[] = []
    let page = 0
    let totalPages = 1
    do {
      const response = await api.quiz.getHistory(page)
      all.push(...response.data.content)
      totalPages = response.data.totalPages
      page += 1
    } while (page < totalPages && page < 100) // hard cap guards against a runaway loop
    if (seq === historySeq) {
      quizHistory.value = all
    }
    return all
  }

  // Wipe all in-memory state on logout so the next account on this browser
  // never sees a flash of the previous user's quiz history. Bumping the
  // sequence counters also invalidates any responses still in flight.
  const reset = () => {
    quizSeq++
    historySeq++
    currentQuiz.value = null
    quizHistory.value = []
    currentScore.value = null
  }

  return {
    currentQuiz,
    quizHistory,
    currentScore,
    fetchQuiz,
    submitQuiz,
    fetchHistory,
    reset
  }
})
