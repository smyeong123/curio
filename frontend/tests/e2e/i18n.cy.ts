import { stubSession, withAuth } from './helpers'

// The edition dropdown: the app boots in English (support.ts pins it), the
// masthead / sidebar menus switch to Korean, and the choice persists.

const emptyPage = { content: [], totalPages: 0, totalElements: 0, number: 0, size: 50 }

describe('Bilingual editions', () => {
  it('boots in English and switches to Korean from the landing masthead', () => {
    cy.visit('/')
    cy.get('html').should('have.attr', 'lang', 'en')
    cy.title().should('eq', 'Curio — A daily for AI model news')
    cy.contains('h1', 'The AI beat').should('be.visible')

    cy.get('header nav select[aria-label="Language"]').select('ko', { force: true })

    cy.get('html').should('have.attr', 'lang', 'ko')
    cy.title().should('eq', 'Curio — 매일 아침 AI 모델 소식')
    cy.contains('h1', '커피보다 먼저').should('be.visible')
    cy.contains('a', '구독하기').should('be.visible')
    cy.contains('관심 주제 고르기').should('exist')
    cy.get('header nav span[lang="ko"]').should('contain', '한국어')
  })

  it('remembers the chosen edition across reloads and routes', () => {
    cy.visit('/')
    cy.get('header nav select[aria-label="Language"]').select('ko', { force: true })
    cy.get('html').should('have.attr', 'lang', 'ko')

    cy.reload()
    cy.get('html').should('have.attr', 'lang', 'ko')
    cy.contains('h1', '커피보다 먼저').should('be.visible')

    cy.visit('/nowhere', { failOnStatusCode: false })
    cy.contains('페이지를 찾을 수 없어요.').should('be.visible')
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

    cy.get('aside select[aria-label="Language"]').select('ko', { force: true })

    cy.get('html').should('have.attr', 'lang', 'ko')
    cy.get('aside').contains('메뉴').should('be.visible')
    cy.get('aside').contains('퀴즈 기록').should('be.visible')
    cy.get('aside').contains('로그아웃').should('be.visible')
    cy.get('aside select[aria-label="언어 선택"]').should('have.value', 'ko')
  })
})
