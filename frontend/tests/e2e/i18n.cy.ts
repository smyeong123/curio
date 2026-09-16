import { stubSession, withAuth } from './helpers'

// The edition toggle: the app boots in English (support.ts pins it), the
// masthead / sidebar controls switch to Korean, and the choice persists.

const emptyPage = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 50 }

describe('Bilingual editions', () => {
  it('boots in English and switches to Korean from the landing masthead', () => {
    cy.visit('/')
    cy.get('html').should('have.attr', 'lang', 'en')
    cy.title().should('eq', 'Curio — A daily for AI model news')
    cy.contains('h1', 'The AI beat').should('be.visible')

    cy.get('header nav button').contains('한국어').click()

    cy.get('html').should('have.attr', 'lang', 'ko')
    cy.title().should('eq', 'Curio — AI 모델 뉴스 일간지')
    cy.contains('h1', '커피보다 먼저').should('be.visible')
    cy.contains('a', '구독하기').should('be.visible')
    cy.contains('토픽을 고르세요').should('exist')
    cy.get('header nav button').should('contain', 'English')
  })

  it('remembers the chosen edition across reloads and routes', () => {
    cy.visit('/')
    cy.get('header nav button').contains('한국어').click()
    cy.get('html').should('have.attr', 'lang', 'ko')

    cy.reload()
    cy.get('html').should('have.attr', 'lang', 'ko')
    cy.contains('h1', '커피보다 먼저').should('be.visible')

    cy.visit('/nowhere', { failOnStatusCode: false })
    cy.contains('그 페이지는 찾을 수 없어요.').should('be.visible')
    cy.contains('홈으로').should('be.visible')
  })

  it('switches from the dashboard sidebar and translates the shell', () => {
    stubSession()
    cy.intercept('GET', '**/news/digests*', { statusCode: 200, body: emptyPage }).as('digests')
    cy.intercept('GET', '**/user/preferences', {
      statusCode: 200,
      body: { topics: ['Claude (Anthropic)', 'Reasoning & Context', 'Pricing & Availability'], deliveryHour: 8, timezone: 'UTC', timezoneAuto: false }
    })

    cy.visit('/dashboard/archive', withAuth())
    cy.wait('@digests')
    cy.get('aside').contains('Sections').should('be.visible')
    cy.get('aside').contains('Sign out').should('be.visible')

    cy.get('aside button[aria-label="Switch to 한국어"]').click()

    cy.get('html').should('have.attr', 'lang', 'ko')
    cy.get('aside').contains('섹션').should('be.visible')
    cy.get('aside').contains('퀴즈 기록').should('be.visible')
    cy.get('aside').contains('로그아웃').should('be.visible')
    cy.get('aside button[aria-label="English로 전환"]').should('exist')
  })
})
