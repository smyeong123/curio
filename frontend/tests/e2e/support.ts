// Cypress support file (loaded before every spec; see cypress.config.ts).
//
// Pin the UI edition to English before the app boots unless the test itself
// already chose one, so copy assertions never depend on the browser's language.
// localStorage is cleared between tests, so each test starts English; within a
// test a chosen edition survives reloads (that is what i18n.cy.ts verifies).
Cypress.on('window:before:load', (win) => {
  if (!win.localStorage.getItem('curio:locale')) {
    win.localStorage.setItem('curio:locale', 'en')
  }
})
