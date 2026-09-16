import { defineConfig } from 'cypress'

export default defineConfig({
  e2e: {
    specPattern: 'tests/e2e/**/*.cy.ts',
    supportFile: 'tests/e2e/support.ts',
    baseUrl: 'http://localhost:5173'
  },
  video: false
})
