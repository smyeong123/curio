import { stubSession, withAuth } from './helpers'

// ArchiveView only shows the last 7 days, so mock dates must be relative.
const daysAgo = (n: number) => new Date(Date.now() - n * 864e5).toISOString()

const digest = (id: string, ageDays: number, beat = 'Claude (Anthropic)') => ({
  id,
  generatedAt: daysAgo(ageDays),
  emailSentAt: daysAgo(ageDays),
  content: {
    generatedFor: [beat],
    summaries: [
      {
        headline: `Headline for ${id}`,
        summary: 'New models show improved reasoning.',
        why_it_matters: 'Better AI could transform industries.',
        source_url: 'https://example.com/ai',
        source_name: 'Tech Daily',
        topic: beat
      }
    ]
  }
})

const page = (content: unknown[], totalPages = 1) => ({
  content,
  totalPages,
  totalElements: content.length,
  number: 0,
  size: 50
})

describe('Archive', () => {
  beforeEach(() => {
    stubSession()
  })

  it('shows empty state when no digests', () => {
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: page([]) }).as('digests')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')
    cy.contains('No issues yet').should('be.visible')
    cy.contains('Set my beats').should('be.visible')
  })

  it('displays a digest with headline, source and delivery status', () => {
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: page([digest('d1', 1)]) }).as('digests')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')
    cy.contains('Headline for d1').should('be.visible')
    cy.contains('Tech Daily').should('be.visible')
    cy.contains('Why it matters').should('be.visible')
    cy.contains('Delivered').should('be.visible')
    cy.contains('Take quiz').should('be.visible')
  })

  it('paginates by day (one day per page)', () => {
    cy.intercept('GET', '**/news/digests*', {
      statusCode: 200,
      body: page([digest('today', 0), digest('yesterday', 1)])
    }).as('digests')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')

    cy.contains('Day 1 of 2').should('be.visible')
    cy.contains('Headline for today').should('be.visible')
    cy.contains('button', 'Newer').should('be.disabled')

    cy.contains('button', 'Older').click()
    cy.contains('Day 2 of 2').should('be.visible')
    cy.contains('Headline for yesterday').should('be.visible')
  })

  it('filters by beat via the collapsible filter', () => {
    cy.intercept('GET', '**/news/digests*', {
      statusCode: 200,
      body: page([digest('claude-issue', 0, 'Claude (Anthropic)'), digest('mistral-issue', 0, 'Mistral')])
    }).as('digests')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')

    // The filter is collapsed by default; open it, then the L1 group, then pick a beat.
    cy.contains('button', 'Filter').click()
    cy.contains('button', 'Frontier Labs').click()
    cy.contains('button', 'Claude (Anthropic)').click()

    cy.contains('Headline for claude-issue').should('be.visible')
    cy.contains('Headline for mistral-issue').should('not.exist')

    cy.get('#beat-filter-panel').contains('button', 'All beats').click()
    cy.contains('Headline for mistral-issue').should('be.visible')
  })

  it('searches the full archive server-side', () => {
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: page([digest('recent', 1)]) }).as('digests')
    // A hit older than the 7-day browse window — search spans the whole archive.
    cy.intercept('GET', '**/news/search*', { statusCode: 200, body: page([digest('old-hit', 20)]) }).as('search')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')

    cy.get('#archive-search').type('reasoning')
    cy.wait('@search')
    cy.contains('1 story matches').should('be.visible')
    cy.contains('Headline for old-hit').should('be.visible')
    cy.contains('search results').should('be.visible')

    cy.contains('button', 'Clear').click()
    cy.contains('Headline for recent').should('be.visible')
  })

  it('narrows a matched issue to only the stories that match the query', () => {
    // Server FTS matches whole digests; the view must not render the matched
    // issue's unrelated sibling stories.
    const multiStory = {
      ...digest('multi', 3),
      content: {
        generatedFor: ['Claude (Anthropic)'],
        summaries: [
          {
            headline: 'OpenAI tests AI understanding of biology',
            summary: 'A new benchmark measures biology reasoning.',
            why_it_matters: 'Science research could speed up.',
            source_url: 'https://example.com/bio',
            source_name: 'Science Daily',
            topic: 'Claude (Anthropic)'
          },
          {
            headline: 'Claude gains longer context windows',
            summary: 'Sessions can now span far more tokens.',
            why_it_matters: 'Bigger projects fit in one conversation.',
            source_url: 'https://example.com/ctx',
            source_name: 'Tech Daily',
            topic: 'Claude (Anthropic)'
          }
        ]
      }
    }
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: page([digest('recent', 1)]) }).as('digests')
    cy.intercept('GET', '**/news/search*', { statusCode: 200, body: page([multiStory]) }).as('search')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')

    cy.get('#archive-search').type('biology')
    cy.wait('@search')
    cy.contains('1 story matches').should('be.visible')
    cy.contains('OpenAI tests AI understanding of biology').should('be.visible')
    cy.contains('Claude gains longer context windows').should('not.exist')
  })

  it('shows a no-matches state when search returns nothing', () => {
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: page([digest('recent', 1)]) }).as('digests')
    cy.intercept('GET', '**/news/search*', { statusCode: 200, body: page([]) }).as('search')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')

    cy.get('#archive-search').type('zebra crossing')
    cy.wait('@search')
    cy.contains('No matches').should('be.visible')

    cy.contains('button', 'Clear search').click()
    cy.contains('Headline for recent').should('be.visible')
  })

  it('shows error state when digests fail to load', () => {
    cy.intercept('GET', '**/news/digests*', {
      statusCode: 500,
      body: { message: 'Server error' }
    }).as('digestsFail')

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digestsFail')
    // The UI surfaces the server's message field when present (generic
    // 'Failed to load digests' is only the fallback for message-less errors).
    cy.contains('Server error').should('be.visible')
    cy.contains('button', 'Retry').should('be.visible')
  })
})
