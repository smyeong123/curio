// Shared session mocking for E2E specs.
//
// The app keeps the access token IN MEMORY only (never localStorage) and
// re-establishes the session on first navigation: if a persisted `user` exists
// but no access token, the router guard calls POST /auth/refresh (which relies
// on an httpOnly cookie in real life). So a mocked signed-in session needs two
// parts: seed localStorage `user`, and stub the silent refresh call.

export interface E2eUser {
  id: string
  email: string
  fullName: string
  isAdmin: boolean
}

export const mockUser: E2eUser = {
  id: 'user-1',
  email: 'test@example.com',
  fullName: 'Test User',
  isAdmin: false
}

export const mockAdmin: E2eUser = {
  id: 'admin-1',
  email: 'admin@example.com',
  fullName: 'Admin User',
  isAdmin: true
}

// Stub the silent-refresh endpoint. Call in beforeEach BEFORE cy.visit.
export const stubSession = (user: E2eUser = mockUser) => {
  cy.intercept('POST', '**/auth/refresh', {
    statusCode: 200,
    body: {
      accessToken: 'e2e-access-token',
      refreshToken: '',
      userId: user.id,
      email: user.email,
      fullName: user.fullName,
      isAdmin: user.isAdmin
    }
  }).as('refresh')
}

// cy.visit options that seed the persisted user before the app boots.
export const withAuth = (user: E2eUser = mockUser) => ({
  onBeforeLoad(win: Window) {
    win.localStorage.setItem('user', JSON.stringify(user))
  }
})
