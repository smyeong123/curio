// Lightweight visual-capture spec: renders the key screens with a mocked
// session and takes screenshots. Assertions exist only to make sure each page
// has actually rendered (styles applied, data loaded) before cy.screenshot.
import { stubSession, withAuth } from './helpers'

const daysAgo = (n: number) => new Date(Date.now() - n * 864e5).toISOString()

const mockDigestPage = {
  content: [
    {
      id: 'digest-1',
      generatedAt: daysAgo(0),
      emailSentAt: daysAgo(0),
      content: {
        generatedFor: ['Claude (Anthropic)', 'Pricing & Availability'],
        summaries: [
          {
            headline: 'Open-source AI assistants accelerate enterprise rollout',
            summary: 'Enterprises are standardizing internal copilots for engineering and support workflows.',
            why_it_matters: 'Faster deployment and lower vendor lock-in can reshape procurement decisions this year.',
            source_url: 'https://example.com/ai-enterprise',
            source_name: 'Tech Daily',
            topic: 'Claude (Anthropic)'
          },
          {
            headline: 'AI startups raise record Series B rounds this quarter',
            summary: 'Venture capital inflow continues as enterprise AI demand drives valuations higher.',
            why_it_matters: 'Sustained funding signals confidence in AI ROI but raises concerns about market concentration.',
            source_url: 'https://example.com/ai-funding',
            source_name: 'Venture Beat',
            topic: 'Pricing & Availability'
          }
        ]
      }
    }
  ],
  totalPages: 1,
  totalElements: 1,
  number: 0,
  size: 10
}

const mockProfile = {
  id: 'user-1',
  email: 'alex@example.com',
  fullName: 'Alex Reader',
  isAdmin: false,
  deliveryEnabled: true,
  emailVerified: true,
  createdAt: '2026-01-15T07:00:00Z'
}

// timezoneAuto=false pins the zone so the layout's ensure-timezone sync never
// tries to PUT an update mid-screenshot.
const mockPreferences = {
  topics: ['Claude (Anthropic)', 'Pricing & Availability', 'Reasoning & Context', 'New & Emerging Models'],
  deliveryHour: 6,
  timezone: 'UTC',
  timezoneAuto: false
}

const mockQuizHistory = {
  content: [
    {
      id: 'attempt-1',
      quizId: 'quiz-1',
      score: 4,
      totalQuestions: 5,
      completedAt: daysAgo(1)
    },
    {
      id: 'attempt-2',
      quizId: 'quiz-2',
      score: 5,
      totalQuestions: 5,
      completedAt: daysAgo(2)
    }
  ],
  totalPages: 1,
  totalElements: 2,
  number: 0
}

// Pin the editorial light theme so screenshots are deterministic regardless of
// the runner's prefers-color-scheme.
const asGuest = () => ({
  onBeforeLoad(win: Window) {
    win.localStorage.setItem('curio:theme', 'light')
  }
})

const asUser = () => {
  const auth = withAuth()
  return {
    onBeforeLoad(win: Window) {
      win.localStorage.setItem('curio:theme', 'light')
      auth.onBeforeLoad(win)
    }
  }
}

const stubApis = () => {
  stubSession()
  cy.intercept('GET', '**/news/digests*', mockDigestPage).as('getDigests')
  cy.intercept('GET', '**/user/me', mockProfile).as('getProfile')
  cy.intercept('GET', '**/user/preferences', mockPreferences).as('getPreferences')
  cy.intercept('PUT', '**/user/preferences', { statusCode: 200, body: mockPreferences }).as('putPreferences')
  cy.intercept('GET', '**/quiz/history*', mockQuizHistory).as('getQuizHistory')
  cy.intercept('POST', '**/auth/logout', { statusCode: 200, body: {} }).as('logout')
}

// Light-theme paper background (--paper: #f3ede1) on <body> proves the
// stylesheet is applied before we screenshot.
const waitForBaseStyles = () => {
  cy.get('body').should('have.css', 'background-color', 'rgb(243, 237, 225)')
}

describe('UI screenshots', () => {
  beforeEach(() => {
    stubApis()
  })

  it('captures desktop screens', () => {
    cy.viewport(1440, 900)

    cy.visit('/', asGuest())
    waitForBaseStyles()
    cy.contains('a', 'Subscribe').should('be.visible')
    cy.screenshot('ui/home-desktop', { capture: 'viewport' })

    cy.visit('/login', asGuest())
    waitForBaseStyles()
    cy.contains('button', 'Sign in').should('be.visible')
    cy.screenshot('ui/login-desktop', { capture: 'viewport' })

    cy.visit('/register', asGuest())
    waitForBaseStyles()
    cy.contains('button', 'Create account').should('be.visible')
    cy.screenshot('ui/register-desktop', { capture: 'viewport' })

    cy.visit('/onboarding', asUser())
    waitForBaseStyles()
    cy.contains('button', 'Start reading').should('be.visible')
    cy.screenshot('ui/onboarding-desktop', { capture: 'viewport' })

    cy.visit('/dashboard/archive', asUser())
    cy.wait('@getDigests')
    waitForBaseStyles()
    cy.contains('h1', 'Today on').should('be.visible')
    cy.contains('Take quiz').should('be.visible')
    cy.screenshot('ui/archive-desktop', { capture: 'viewport' })

    cy.visit('/dashboard/settings', asUser())
    cy.wait('@getProfile')
    cy.wait('@getPreferences')
    waitForBaseStyles()
    cy.contains('button', 'Save changes').should('be.visible')
    cy.screenshot('ui/settings-desktop', { capture: 'viewport' })

    cy.visit('/dashboard/quiz-history', asUser())
    cy.wait('@getQuizHistory')
    waitForBaseStyles()
    cy.contains('h1', 'What you').should('be.visible')
    cy.screenshot('ui/quiz-history-desktop', { capture: 'viewport' })
  })

  it('captures mobile screens', () => {
    cy.viewport(390, 844)

    cy.visit('/', asGuest())
    waitForBaseStyles()
    cy.contains('a', 'Subscribe').should('be.visible')
    cy.screenshot('ui/home-mobile', { capture: 'viewport' })

    cy.visit('/dashboard/archive', asUser())
    cy.wait('@getDigests')
    waitForBaseStyles()
    cy.get('button[aria-label="Open menu"]').should('be.visible')
    cy.screenshot('ui/archive-mobile', { capture: 'viewport' })

    cy.get('button[aria-label="Open menu"]').click()
    cy.get('[role="dialog"][aria-label="Main menu"]').contains('Sections').should('be.visible')
    cy.screenshot('ui/archive-mobile-menu', { capture: 'viewport' })
  })
})
