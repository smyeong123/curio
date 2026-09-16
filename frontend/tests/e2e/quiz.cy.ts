import { stubSession, withAuth } from './helpers'

// GET /quiz/digest/{id} no longer returns correct answers or explanations —
// scoring is server-side, so the quiz payload carries questions/options only.
const mockQuiz = {
  id: 'quiz-1',
  digestId: 'digest-1',
  questions: {
    questions: [
      { id: 1, question: 'What is AI?', options: { A: 'Art', B: 'Intelligence', C: 'Algorithm', D: 'None' } },
      { id: 2, question: 'Which lab released it?', options: { A: 'Meta', B: 'xAI', C: 'Anthropic', D: 'Mistral' } }
    ]
  }
}

// POST /quiz/{quizId}/submit returns this attempt's score plus the
// "better score wins" retake outcome (bestScore + improved).
const mockSubmitResponse = {
  score: 2,
  totalQuestions: 2,
  results: [
    { questionId: 1, correct: true, correctAnswer: 'B', explanation: 'AI stands for Artificial Intelligence' },
    { questionId: 2, correct: true, correctAnswer: 'C', explanation: 'It was released by Anthropic' }
  ],
  bestScore: 2,
  improved: true
}

describe('Quiz', () => {
  beforeEach(() => {
    stubSession()
  })

  it('loads quiz and displays questions', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: mockQuiz
    }).as('getQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')
    cy.contains('Five questions').should('be.visible')
    cy.contains('What is AI?').should('be.visible')
    cy.contains('Which lab released it?').should('be.visible')
  })

  it('shows the prior best score when revisiting an already-taken quiz', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: { ...mockQuiz, previousAttempt: { score: 1, completedAt: '2026-07-03T09:30:00' } }
    }).as('getQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')
    cy.contains('your best is 1/2').should('be.visible')
    cy.contains('A better run replaces it').should('be.visible')
  })

  it('shows no prior-attempt banner on a first take', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: mockQuiz
    }).as('getQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')
    // Assert on the real banner copy (see the previousAttempt test above) —
    // a made-up string would pass trivially whether or not the banner renders.
    cy.contains('your best is').should('not.exist')
    cy.contains('A better run replaces it').should('not.exist')
  })

  it('tracks progress and disables submit until all answered', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: mockQuiz
    }).as('getQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')

    cy.contains('0/2').should('be.visible')
    cy.contains('button', 'File my answers').should('be.disabled')
    cy.contains('Answer all 2 questions to submit').should('be.visible')

    // Answer question 1
    cy.contains('Intelligence').click()
    cy.contains('1/2').should('be.visible')
    cy.contains('button', 'File my answers').should('be.disabled')

    // Answer question 2
    cy.contains('Anthropic').click()
    cy.contains('2/2').should('be.visible')
    cy.contains('button', 'File my answers').should('not.be.disabled')
  })

  it('submits quiz and shows results', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: mockQuiz
    }).as('getQuiz')

    cy.intercept('POST', '**/quiz/quiz-1/submit', {
      statusCode: 200,
      body: mockSubmitResponse
    }).as('submitQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')

    cy.contains('Intelligence').click()
    cy.contains('Anthropic').click()
    cy.contains('button', 'File my answers').click()

    cy.wait('@submitQuiz').its('request.body').should('deep.equal', {
      answers: { '1': 'B', '2': 'C' }
    })
    cy.contains('2/2').should('be.visible') // score
    cy.contains('100% correct').should('be.visible') // percentage
    cy.contains('Excellent').should('be.visible') // tier message
    cy.contains('button', 'Review answers').should('be.visible')
  })

  it('allows reviewing answers after submission', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: mockQuiz
    }).as('getQuiz')

    cy.intercept('POST', '**/quiz/quiz-1/submit', {
      statusCode: 200,
      body: mockSubmitResponse
    }).as('submitQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')

    cy.contains('Intelligence').click()
    cy.contains('Anthropic').click()
    cy.contains('button', 'File my answers').click()
    cy.wait('@submitQuiz')

    cy.contains('button', 'Review answers').click()
    cy.contains('Correct!').should('be.visible')
    cy.contains('AI stands for Artificial Intelligence').should('be.visible')
    // Correct answers only revealed post-submission, marked in the option list
    cy.contains('button', 'Back to results').should('be.visible')
  })

  it('keeps the prior best score on a worse retake (improved=false)', () => {
    cy.intercept('GET', '**/quiz/digest/digest-1', {
      statusCode: 200,
      body: mockQuiz
    }).as('getQuiz')

    cy.intercept('POST', '**/quiz/quiz-1/submit', {
      statusCode: 200,
      body: {
        score: 1,
        totalQuestions: 2,
        results: [
          { questionId: 1, correct: true, correctAnswer: 'B', explanation: 'AI stands for Artificial Intelligence' },
          { questionId: 2, correct: false, correctAnswer: 'C', explanation: 'It was released by Anthropic' }
        ],
        bestScore: 2,
        improved: false
      }
    }).as('submitQuiz')

    cy.visit('/dashboard/quiz/digest-1', withAuth())
    cy.wait('@getQuiz')

    cy.contains('Intelligence').click()
    cy.contains('Mistral').click()
    cy.contains('button', 'File my answers').click()
    cy.wait('@submitQuiz')

    cy.contains('1/2').should('be.visible')
    cy.contains('Your best for this quiz stays 2/2').should('be.visible')
  })

  it('shows error when quiz not found', () => {
    cy.intercept('GET', '**/quiz/digest/missing', {
      statusCode: 404,
      body: { message: 'Quiz not found' }
    }).as('quizNotFound')

    cy.visit('/dashboard/quiz/missing', withAuth())
    cy.wait('@quizNotFound')
    cy.contains('Quiz not found for this digest.').should('be.visible')
    cy.contains("Back to today's edition").should('be.visible')
  })
})

describe('Quiz History', () => {
  beforeEach(() => {
    stubSession()
  })

  it('shows empty state when no history', () => {
    cy.intercept('GET', '**/quiz/history*', {
      statusCode: 200,
      body: { content: [], totalPages: 0, totalElements: 0, number: 0 }
    }).as('history')

    cy.visit('/dashboard/quiz-history', withAuth())
    cy.wait('@history')
    cy.contains('Nothing on the').should('be.visible')
    cy.contains("Go to today's edition").should('be.visible')
  })

  it('displays quiz attempt history with stats', () => {
    cy.intercept('GET', '**/quiz/history*', {
      statusCode: 200,
      body: {
        content: [
          { id: 'a-1', quizId: 'q-1', score: 4, totalQuestions: 5, completedAt: '2026-03-15T09:00:00Z' },
          { id: 'a-2', quizId: 'q-2', score: 2, totalQuestions: 5, completedAt: '2026-03-14T09:00:00Z' }
        ],
        totalPages: 1,
        totalElements: 2,
        number: 0
      }
    }).as('history')

    cy.visit('/dashboard/quiz-history', withAuth())
    cy.wait('@history')
    cy.contains('remembered').should('be.visible')
    cy.contains('4/5').should('be.visible')
    cy.contains('2/5').should('be.visible')
    cy.contains('Quizzes filed').should('be.visible')
    cy.contains('Average score').should('be.visible')
    cy.contains('Personal best').should('be.visible')
  })
})
