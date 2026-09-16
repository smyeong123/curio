export interface QuizQuestion {
  id: number
  question: string
  options: Record<string, string>
  correct?: string
  explanation?: string
}

export interface Quiz {
  id: string
  digestId: string
  questions: {
    questions: QuizQuestion[]
  }
  /** The caller's existing attempt on this quiz (retakes are "better score wins"), null/absent if untaken. */
  previousAttempt?: {
    score: number
    completedAt: string | null
  } | null
}

export interface QuizAttempt {
  id: string
  quizId: string
  score: number
  totalQuestions: number
  completedAt: string
}

export interface QuizSubmitResponse {
  score: number
  totalQuestions: number
  results: {
    questionId: number
    correct: boolean
    correctAnswer: string
    explanation: string
  }[]
  // "Better score wins" retake outcome (V25). Optional for backward compatibility.
  bestScore?: number
  improved?: boolean
}

export interface QuizHistoryPage {
  content: QuizAttempt[]
  totalPages: number
  totalElements: number
  number: number
}
