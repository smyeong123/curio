import { stubSession, withAuth } from './helpers'

// Onboarding = beat (topic) selection over the 3-level accordion, 3-leaf minimum.
// Selecting via the accordion: L1 domain -> L2 subcategory -> L3 leaf buttons.

const expandProprietaryFrontier = () => {
  cy.contains('button', 'Frontier Labs').click()
  cy.contains('button', 'Proprietary Frontier').click()
}

describe('Onboarding', () => {
  beforeEach(() => {
    stubSession()
    // ArchiveView loads digests right after the post-save redirect.
    cy.intercept('GET', '**/news/digests*', {
      statusCode: 200,
      body: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 50 }
    }).as('digests')
  })

  it('renders the beat picker with all four domains collapsed', () => {
    cy.visit('/onboarding', withAuth())

    cy.contains('your beat').should('be.visible')
    cy.contains('minimum · pick as many as you like').should('be.visible')

    cy.contains('button', 'Frontier Labs').should('be.visible')
    cy.contains('button', 'Agentic & Developer Tools').should('be.visible')
    cy.contains('button', 'Capabilities & Ecosystem').should('be.visible')
    cy.contains('button', 'Emerging').should('be.visible')

    // Leaves stay hidden until the accordion is expanded.
    cy.contains('Claude (Anthropic)').should('not.exist')
  })

  it('expands domain and subcategory to reveal leaf topics', () => {
    cy.visit('/onboarding', withAuth())

    cy.contains('button', 'Frontier Labs').click()
    cy.contains('button', 'Proprietary Frontier').should('be.visible')
    cy.contains('button', 'Open-Weight Leaders').should('be.visible')
    cy.contains('Claude (Anthropic)').should('not.exist')

    cy.contains('button', 'Proprietary Frontier').click()
    cy.contains('button', 'Claude (Anthropic)').should('be.visible')
    cy.contains('button', 'GPT & ChatGPT (OpenAI)').should('be.visible')
    cy.contains('button', 'Grok (xAI)').should('be.visible')
  })

  it('keeps Start reading disabled until 3 beats are selected', () => {
    cy.visit('/onboarding', withAuth())

    cy.contains('button', 'Start reading').should('be.disabled')

    expandProprietaryFrontier()
    cy.contains('button', 'Claude (Anthropic)').click()
    cy.contains('2 more to go').should('be.visible')
    cy.contains('button', 'Start reading').should('be.disabled')

    cy.contains('button', 'GPT & ChatGPT (OpenAI)').click()
    cy.contains('1 more to go').should('be.visible')
    cy.contains('button', 'Start reading').should('be.disabled')

    cy.contains('button', 'Gemini (Google DeepMind)').click()
    cy.contains("you're all set").should('be.visible')
    cy.contains('button', 'Start reading').should('not.be.disabled')

    // Deselecting drops back under the minimum.
    cy.contains('button', 'Gemini (Google DeepMind)').click()
    cy.contains('button', 'Start reading').should('be.disabled')
  })

  it('saves beats with the captured timezone and redirects to the archive', () => {
    cy.intercept('PUT', '**/user/preferences', {
      statusCode: 200,
      body: {}
    }).as('savePrefs')

    cy.visit('/onboarding', withAuth())

    // Preset bundle selects a full set of beats in one click.
    cy.contains('button', 'Frontier Watcher').click()
    cy.contains('button', 'Start reading').should('not.be.disabled').click()

    cy.wait('@savePrefs').its('request.body').should(body => {
      expect(body.topics).to.include('Claude (Anthropic)')
      expect(body.topics).to.have.length.of.at.least(3)
      expect(body.deliveryHour).to.eq(6)
      expect(body.timezone).to.be.a('string').and.not.be.empty
    })

    cy.contains('Beats saved').should('be.visible')
    cy.url().should('include', '/dashboard/archive')
  })

  it('shows an error toast and stays on onboarding when save fails', () => {
    cy.intercept('PUT', '**/user/preferences', {
      statusCode: 500,
      body: {}
    }).as('saveFail')

    cy.visit('/onboarding', withAuth())

    expandProprietaryFrontier()
    cy.contains('button', 'Claude (Anthropic)').click()
    cy.contains('button', 'GPT & ChatGPT (OpenAI)').click()
    cy.contains('button', 'Gemini (Google DeepMind)').click()
    cy.contains('button', 'Start reading').click()

    cy.wait('@saveFail')
    cy.contains('Failed to save preferences').should('be.visible')
    cy.url().should('include', '/onboarding')
  })
})
