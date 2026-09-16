describe('404 Page', () => {
  it('shows 404 for unknown routes', () => {
    cy.visit('/some/nonexistent/page', { failOnStatusCode: false })
    cy.contains('404').should('be.visible')
    cy.contains("We couldn't find that").should('be.visible')
  })

  it('has navigation buttons', () => {
    cy.visit('/random-page', { failOnStatusCode: false })
    cy.contains('Go back').should('be.visible')
    cy.contains('Go home').should('be.visible')
  })

  it('navigates home from 404 page', () => {
    cy.visit('/nonexistent', { failOnStatusCode: false })
    cy.contains('Go home').click()
    cy.url().should('eq', Cypress.config('baseUrl') + '/')
  })
})
