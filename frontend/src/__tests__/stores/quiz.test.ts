import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useQuizStore } from '@/stores/quiz'

vi.mock('@/services/api', () => ({
  api: {
    quiz: {
      getQuiz: vi.fn(),
      submitQuiz: vi.fn(),
      getHistory: vi.fn()
    }
  }
}))

import { api } from '@/services/api'

const mockQuiz = {
  id: 'quiz-1',
  digestId: 'digest-1',
  questions: {
    questions: [
      { id: 1, question: 'What happened?', options: { A: 'This', B: 'That', C: 'Other', D: 'None' }, correct: 'A', explanation: 'Because' }
    ]
  }
}

const mockSubmitResponse = {
  score: 4,
  totalQuestions: 5,
  results: [
    { questionId: 1, correct: true, correctAnswer: 'A', explanation: 'Because' }
  ]
}

describe('quiz store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with null state', () => {
    const store = useQuizStore()
    expect(store.currentQuiz).toBeNull()
    expect(store.quizHistory).toEqual([])
    expect(store.currentScore).toBeNull()
  })

  it('fetchQuiz sets quiz and resets score', async () => {
    vi.mocked(api.quiz.getQuiz).mockResolvedValue({ data: mockQuiz } as any)
    const store = useQuizStore()
    store.currentScore = 3

    await store.fetchQuiz('digest-1')

    expect(store.currentQuiz).toEqual(mockQuiz)
    expect(store.currentScore).toBeNull()
    expect(api.quiz.getQuiz).toHaveBeenCalledWith('digest-1')
  })

  it('submitQuiz sets score', async () => {
    vi.mocked(api.quiz.submitQuiz).mockResolvedValue({ data: mockSubmitResponse } as any)
    const store = useQuizStore()

    const result = await store.submitQuiz('quiz-1', { 1: 'A', 2: 'B' })

    expect(store.currentScore).toBe(4)
    expect(result.score).toBe(4)
    expect(api.quiz.submitQuiz).toHaveBeenCalledWith('quiz-1', { 1: 'A', 2: 'B' })
  })

  it('fetchHistory sets history', async () => {
    const history = [{ id: 'a-1', quizId: 'q-1', score: 4, totalQuestions: 5, completedAt: '2026-03-15' }]
    vi.mocked(api.quiz.getHistory).mockResolvedValue({
      data: { content: history, totalPages: 1, totalElements: 1, number: 0 }
    } as any)
    const store = useQuizStore()

    await store.fetchHistory()

    expect(store.quizHistory).toEqual(history)
  })

  it('reset wipes quiz state (logout must not leak across accounts)', async () => {
    vi.mocked(api.quiz.getQuiz).mockResolvedValue({ data: mockQuiz } as any)
    const store = useQuizStore()
    await store.fetchQuiz('digest-1')
    store.currentScore = 4
    store.quizHistory = [{ id: 'a-1', quizId: 'q-1', score: 4, totalQuestions: 5, completedAt: '2026-03-15' } as any]

    store.reset()

    expect(store.currentQuiz).toBeNull()
    expect(store.quizHistory).toEqual([])
    expect(store.currentScore).toBeNull()
  })

  it('a stale fetchQuiz response does not clobber a newer one', async () => {
    const staleQuiz = { ...mockQuiz, id: 'quiz-stale' }
    const freshQuiz = { ...mockQuiz, id: 'quiz-fresh' }
    let resolveStale: (v: unknown) => void
    const stalePromise = new Promise((resolve) => { resolveStale = resolve })
    vi.mocked(api.quiz.getQuiz)
      .mockReturnValueOnce(stalePromise as any)
      .mockResolvedValueOnce({ data: freshQuiz } as any)
    const store = useQuizStore()

    const first = store.fetchQuiz('digest-stale')
    await store.fetchQuiz('digest-fresh')
    resolveStale!({ data: staleQuiz })
    await first

    expect(store.currentQuiz?.id).toBe('quiz-fresh')
  })
})
