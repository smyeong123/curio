describe('Reset password smoke', () => {
  it('requests a password reset link', () => {
    cy.intercept('POST', '**/auth/forgot-password', {
      statusCode: 200,
      body: { message: 'If an account exists for this email, a reset link has been sent.' }
    }).as('forgotPassword')

    cy.visit('/reset-password')
    cy.get('#reset-email').type('user@example.com')
    cy.contains('button', 'Send reset link').click()

    cy.wait('@forgotPassword')
    cy.contains('Check your inbox').should('be.visible')
    cy.contains('user@example.com').should('be.visible')
  })

  it('submits new password with token', () => {
    cy.intercept('POST', '**/auth/reset-password', {
      statusCode: 200,
      body: { message: 'Password has been reset successfully.' }
    }).as('resetPassword')

    cy.visit('/reset-password?token=test-token')
    cy.get('#new-password').type('Password123!')
    cy.get('#confirm-password').type('Password123!')
    cy.contains('button', 'Update password').click()

    cy.wait('@resetPassword')
    cy.contains('Your password is set').should('be.visible')
    cy.contains('Sign in').should('be.visible')
  })
})
