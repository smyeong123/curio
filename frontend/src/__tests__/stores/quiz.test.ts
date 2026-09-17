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

const attempt = (id: string) => ({ id, quizId: 'q-1', score: 4, totalQuestions: 5, completedAt: '2026-03-15' })

const pageOf = (content: unknown[], number: number, totalPages: number) => ({
  data: { content, totalPages, totalElements: content.length * totalPages, number }
})

describe('quiz store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with an empty history', () => {
    const store = useQuizStore()
    expect(store.quizHistory).toEqual([])
  })

  it('fetchQuiz returns the quiz for a digest', async () => {
    vi.mocked(api.quiz.getQuiz).mockResolvedValue({ data: mockQuiz } as any)
    const store = useQuizStore()

    const quiz = await store.fetchQuiz('digest-1')

    expect(quiz).toEqual(mockQuiz)
    expect(api.quiz.getQuiz).toHaveBeenCalledWith('digest-1')
  })

  it('submitQuiz returns the graded result', async () => {
    vi.mocked(api.quiz.submitQuiz).mockResolvedValue({ data: mockSubmitResponse } as any)
    const store = useQuizStore()

    const result = await store.submitQuiz('quiz-1', { 1: 'A', 2: 'B' })

    expect(result.score).toBe(4)
    expect(api.quiz.submitQuiz).toHaveBeenCalledWith('quiz-1', { 1: 'A', 2: 'B' })
  })

  it('fetchHistory sets history from a single page', async () => {
    const history = [attempt('a-1')]
    vi.mocked(api.quiz.getHistory).mockResolvedValue(pageOf(history, 0, 1) as any)
    const store = useQuizStore()

    await store.fetchHistory()

    expect(store.quizHistory).toEqual(history)
    expect(api.quiz.getHistory).toHaveBeenCalledTimes(1)
    expect(api.quiz.getHistory).toHaveBeenCalledWith(0)
  })

  it('fetchHistory requests the remaining pages together and keeps them in order', async () => {
    let releasePage1!: () => void
    vi.mocked(api.quiz.getHistory).mockImplementation(((page: number) => {
      if (page === 0) return Promise.resolve(pageOf([attempt('a-p0')], 0, 3))
      if (page === 1) return new Promise((resolve) => { releasePage1 = () => resolve(pageOf([attempt('a-p1')], 1, 3)) })
      return Promise.resolve(pageOf([attempt('a-p2')], 2, 3))
    }) as any)
    const store = useQuizStore()

    const pending = store.fetchHistory()
    await vi.waitFor(() => expect(api.quiz.getHistory).toHaveBeenCalledTimes(3))
    expect(api.quiz.getHistory).toHaveBeenNthCalledWith(2, 1)
    expect(api.quiz.getHistory).toHaveBeenNthCalledWith(3, 2)

    releasePage1()
    await pending

    expect(store.quizHistory.map((a) => a.id)).toEqual(['a-p0', 'a-p1', 'a-p2'])
  })

  it('reset wipes quiz state (logout must not leak across accounts)', async () => {
    vi.mocked(api.quiz.getHistory).mockResolvedValue(pageOf([attempt('a-1')], 0, 1) as any)
    const store = useQuizStore()
    await store.fetchHistory()

    store.reset()

    expect(store.quizHistory).toEqual([])
  })

  it('a stale fetchHistory response does not clobber a newer one', async () => {
    let resolveStale: (v: unknown) => void
    const stalePromise = new Promise((resolve) => { resolveStale = resolve })
    vi.mocked(api.quiz.getHistory)
      .mockReturnValueOnce(stalePromise as any)
      .mockResolvedValueOnce(pageOf([attempt('a-fresh')], 0, 1) as any)
    const store = useQuizStore()

    const first = store.fetchHistory()
    await store.fetchHistory()
    resolveStale!(pageOf([attempt('a-stale')], 0, 1))
    await first

    expect(store.quizHistory.map((a) => a.id)).toEqual(['a-fresh'])
  })
})
