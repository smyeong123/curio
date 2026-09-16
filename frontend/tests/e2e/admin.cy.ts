import { stubSession, withAuth, mockAdmin, mockUser } from './helpers'

const springPage = (content: unknown[], overrides: Record<string, unknown> = {}) => ({
  content,
  number: 0,
  size: 20,
  totalPages: content.length > 0 ? 1 : 0,
  totalElements: content.length,
  ...overrides
})

const auditEntry = (id: number, overrides: Record<string, unknown> = {}) => ({
  id,
  actorId: 'admin-1',
  actorEmail: 'admin@example.com',
  action: 'GENERATE_DIGESTS',
  targetType: 'DIGEST',
  targetId: '44444444-4444-4444-4444-444444444444',
  requestId: `req-${id}`,
  metadata: { topic: 'Claude (Anthropic)' },
  createdAt: '2026-06-30T12:00:00Z',
  ...overrides
})

describe('Admin smoke', () => {
  beforeEach(() => {
    stubSession(mockAdmin)
  })

  it('loads users page and navigates to detail', () => {
    cy.intercept('GET', '**/admin/users?*', {
      statusCode: 200,
      body: springPage([
        {
          id: '33333333-3333-3333-3333-333333333333',
          email: 'user@curio.test',
          fullName: 'Test User',
          topicsCount: 4,
          deliveryEnabled: true,
          createdAt: '2026-02-01T00:00:00Z'
        }
      ])
    }).as('adminUsers')

    // Registered after the list intercept so it wins for the detail URL.
    cy.intercept('GET', '**/admin/users/33333333-3333-3333-3333-333333333333', {
      statusCode: 200,
      body: {
        user: {
          id: '33333333-3333-3333-3333-333333333333',
          email: 'user@curio.test',
          fullName: 'Test User',
          isAdmin: false,
          deliveryEnabled: true,
          emailVerified: true,
          createdAt: '2026-02-01T00:00:00Z'
        },
        topics: ['Claude (Anthropic)', 'New & Emerging Models'],
        metrics: { digestCount: 5, quizAttempts: 3, averageQuizScore: 4.0 },
        recentDigests: [],
        recentQuizAttempts: []
      }
    }).as('adminUserDetail')

    cy.visit('/admin/users', withAuth(mockAdmin))
    cy.wait('@adminUsers')
    cy.contains('user@curio.test').should('be.visible')
    cy.contains('View').click()
    cy.wait('@adminUserDetail')
    cy.contains('Reader file').should('be.visible')
    cy.contains('Test User').should('be.visible')
    cy.contains('Claude (Anthropic)').should('be.visible')
  })

  it('loads stats page', () => {
    // Scoped to the API path so the intercept never swallows the /admin/stats
    // page navigation itself.
    cy.intercept('GET', '**/api/v1/admin/stats', {
      statusCode: 200,
      body: {
        totalUsers: 42,
        emailsSentToday: 16,
        quizCompletionsToday: 9
      }
    }).as('adminStats')

    cy.intercept('GET', '**/api/v1/admin/stats/topics', {
      statusCode: 200,
      body: {
        'Claude (Anthropic)': 20,
        'New & Emerging Models': 15
      }
    }).as('topicDistribution')

    cy.visit('/admin/stats', withAuth(mockAdmin))
    cy.wait('@adminStats')
    cy.wait('@topicDistribution')
    cy.contains('Total readers').should('be.visible')
    cy.contains('42').should('be.visible')
    cy.contains('Claude (Anthropic)').should('be.visible')
    cy.contains('20').should('be.visible')
  })

  it('redirects a non-admin user away from admin pages', () => {
    stubSession(mockUser)
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: springPage([]) }).as('digests')

    cy.visit('/admin/users', withAuth(mockUser))
    // requiresAdmin guard bounces to home, which redirects authenticated users to archive.
    cy.location('pathname').should('eq', '/dashboard/archive')
    cy.contains('user@curio.test').should('not.exist')
  })
})

describe('Admin audit log', () => {
  beforeEach(() => {
    stubSession(mockAdmin)
  })

  it('renders ledger entries with when, actor, action, target and detail', () => {
    cy.intercept('GET', '**/admin/audit-log*', {
      statusCode: 200,
      body: springPage([
        auditEntry(1),
        auditEntry(2, {
          action: 'SEND_EMAILS',
          targetType: null,
          targetId: null,
          metadata: null
        })
      ])
    }).as('auditLog')

    cy.visit('/admin/audit', withAuth(mockAdmin))
    cy.wait('@auditLog')

    // Column headers
    cy.contains('th', 'When').should('be.visible')
    cy.contains('th', 'Actor').should('be.visible')
    cy.contains('th', 'Action').should('be.visible')
    cy.contains('th', 'Target').should('be.visible')
    cy.contains('th', 'Detail').should('be.visible')

    // Entry with full details
    cy.contains('Jun 30, 2026').should('be.visible')
    cy.contains('admin@example.com').should('be.visible')
    cy.contains('GENERATE_DIGESTS').should('be.visible')
    cy.contains('DIGEST').should('be.visible')
    cy.contains('44444444…').should('be.visible')
    cy.contains('topic=Claude (Anthropic)').should('be.visible')

    // Entry without target/metadata renders placeholders
    cy.contains('SEND_EMAILS').should('be.visible')

    // Single page: no pagination controls
    cy.contains('button', 'Older').should('not.exist')
  })

  it('paginates when there is more than one page', () => {
    cy.intercept('GET', '**/admin/audit-log*', (req) => {
      const page = Number(req.query.page ?? 0)
      req.reply({
        statusCode: 200,
        body: springPage(
          page === 0
            ? [auditEntry(1, { action: 'PAGE_ONE_ACTION' })]
            : [auditEntry(2, { action: 'PAGE_TWO_ACTION' })],
          { number: page, totalPages: 2, totalElements: 2 }
        )
      })
    }).as('auditLog')

    cy.visit('/admin/audit', withAuth(mockAdmin))
    cy.wait('@auditLog')

    cy.contains('PAGE_ONE_ACTION').should('be.visible')
    cy.contains('button', 'Newer').should('be.disabled')
    cy.contains('button', 'Older').should('not.be.disabled')

    cy.contains('button', 'Older').click()
    cy.wait('@auditLog')
    cy.contains('PAGE_TWO_ACTION').should('be.visible')
    cy.contains('PAGE_ONE_ACTION').should('not.exist')
    cy.contains('button', 'Older').should('be.disabled')
    cy.contains('button', 'Newer').should('not.be.disabled')
  })

  it('shows the empty state when the ledger has no entries', () => {
    cy.intercept('GET', '**/admin/audit-log*', {
      statusCode: 200,
      body: springPage([])
    }).as('auditLog')

    cy.visit('/admin/audit', withAuth(mockAdmin))
    cy.wait('@auditLog')

    cy.contains('Ledger is empty').should('be.visible')
    cy.contains('No admin actions recorded yet.').should('be.visible')
  })

  it('shows an error state with retry when loading fails', () => {
    cy.intercept('GET', '**/admin/audit-log*', {
      statusCode: 500,
      body: { message: 'Server error' }
    }).as('auditLogFail')

    cy.visit('/admin/audit', withAuth(mockAdmin))
    cy.wait('@auditLogFail')

    cy.contains('button', 'Retry').should('be.visible')

    // Retry refetches and recovers.
    cy.intercept('GET', '**/admin/audit-log*', {
      statusCode: 200,
      body: springPage([auditEntry(1)])
    }).as('auditLogOk')

    cy.contains('button', 'Retry').click()
    cy.wait('@auditLogOk')
    cy.contains('GENERATE_DIGESTS').should('be.visible')
  })
})
