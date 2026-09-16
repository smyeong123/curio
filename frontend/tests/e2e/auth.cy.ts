import { stubSession, withAuth } from './helpers'

const mockAuthResponse = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
  userId: 'user-1',
  email: 'test@example.com',
  fullName: 'Test User',
  isAdmin: false
}

describe('Authentication', () => {
  describe('Login', () => {
    it('shows validation errors for empty form', () => {
      cy.visit('/login')
      cy.contains('button', 'Sign in').click()
      cy.contains('Email is required').should('be.visible')
      cy.contains('Password is required').should('be.visible')
    })

    it('sends a verification code, then signs in after the code is entered', () => {
      // Step 1: valid password → a code is emailed (no session yet).
      cy.intercept('POST', '**/auth/login', {
        statusCode: 200,
        body: {
          status: 'VERIFICATION_REQUIRED',
          challengeId: 'challenge-1',
          email: 'test@example.com',
          attemptsRemaining: 5,
          expiresInSeconds: 600
        }
      }).as('login')

      // Step 2: correct code → the session is issued.
      cy.intercept('POST', '**/auth/verify-code', {
        statusCode: 200,
        body: { status: 'VERIFIED', resetAvailable: false, auth: mockAuthResponse }
      }).as('verify')

      cy.intercept('GET', '**/news/digests*', {
        statusCode: 200,
        body: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 10 }
      }).as('digests')

      cy.visit('/login')
      cy.get('#login-email').type('test@example.com')
      cy.get('#login-password').type('password123')
      cy.contains('button', 'Sign in').click()

      cy.wait('@login')
      cy.url().should('include', '/verify')
      cy.contains('5 attempts remaining').should('be.visible')

      cy.get('#verify-code').type('123456')
      cy.contains('button', 'Verify').click()

      cy.wait('@verify')
      cy.url().should('include', '/dashboard/archive')
    })

    it('signs in directly when the server says AUTHENTICATED (2FA off)', () => {
      cy.intercept('POST', '**/auth/login', {
        statusCode: 200,
        body: { status: 'AUTHENTICATED', auth: mockAuthResponse }
      }).as('login')

      cy.intercept('GET', '**/news/digests*', {
        statusCode: 200,
        body: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 10 }
      }).as('digests')

      cy.visit('/login')
      cy.get('#login-email').type('test@example.com')
      cy.get('#login-password').type('password123')
      cy.contains('button', 'Sign in').click()

      cy.wait('@login')
      cy.url().should('include', '/dashboard/archive')
    })

    it('shows the error and decrements attempts on a wrong code', () => {
      cy.intercept('POST', '**/auth/login', {
        statusCode: 200,
        body: {
          status: 'VERIFICATION_REQUIRED',
          challengeId: 'challenge-1',
          email: 'test@example.com',
          attemptsRemaining: 5,
          expiresInSeconds: 600
        }
      }).as('login')

      cy.intercept('POST', '**/auth/verify-code', {
        statusCode: 200,
        body: {
          status: 'INVALID_CODE',
          resetAvailable: false,
          attemptsRemaining: 4,
          message: "That code isn't right."
        }
      }).as('verify')

      cy.visit('/login')
      cy.get('#login-email').type('test@example.com')
      cy.get('#login-password').type('password123')
      cy.contains('button', 'Sign in').click()

      cy.wait('@login')
      cy.contains('5 attempts remaining').should('be.visible')
      cy.get('#verify-code').type('999999')
      cy.contains('button', 'Verify').click()

      cy.wait('@verify')
      cy.contains("That code isn't right.").should('be.visible')
      cy.contains('4 attempts remaining').should('be.visible')
    })

    it('resends a fresh code from the verify step', () => {
      cy.intercept('POST', '**/auth/login', {
        statusCode: 200,
        body: {
          status: 'VERIFICATION_REQUIRED',
          challengeId: 'challenge-1',
          email: 'test@example.com',
          attemptsRemaining: 5,
          expiresInSeconds: 600
        }
      }).as('login')

      cy.intercept('POST', '**/auth/resend-code', {
        statusCode: 200,
        body: { status: 'SENT', attemptsRemaining: 5, expiresInSeconds: 600 }
      }).as('resend')

      cy.visit('/login')
      cy.get('#login-email').type('test@example.com')
      cy.get('#login-password').type('password123')
      cy.contains('button', 'Sign in').click()

      cy.wait('@login')
      cy.url().should('include', '/verify')
      cy.contains('button', 'Resend code').click()

      cy.wait('@resend')
      cy.contains('A new code is on its way.').should('be.visible')
    })

    it('shows attempts remaining and offers a reset after too many wrong codes', () => {
      cy.intercept('POST', '**/auth/login', {
        statusCode: 200,
        body: {
          status: 'VERIFICATION_REQUIRED',
          challengeId: 'challenge-1',
          email: 'test@example.com',
          attemptsRemaining: 1,
          expiresInSeconds: 600
        }
      }).as('login')

      cy.intercept('POST', '**/auth/verify-code', {
        statusCode: 200,
        body: {
          status: 'LOCKED',
          resetAvailable: true,
          attemptsRemaining: 0,
          message: 'Too many incorrect codes.'
        }
      }).as('verify')

      cy.visit('/login')
      cy.get('#login-email').type('test@example.com')
      cy.get('#login-password').type('password123')
      cy.contains('button', 'Sign in').click()

      cy.wait('@login')
      cy.get('#verify-code').type('000000')
      cy.contains('button', 'Verify').click()

      cy.wait('@verify')
      cy.contains('Send password reset email').should('be.visible')
    })

    it('shows server error on invalid credentials', () => {
      cy.intercept('POST', '**/auth/login', {
        statusCode: 401,
        body: { message: 'Invalid email or password' }
      }).as('loginFail')

      cy.visit('/login')
      cy.get('#login-email').type('wrong@example.com')
      cy.get('#login-password').type('wrongpass')
      cy.contains('button', 'Sign in').click()

      cy.wait('@loginFail')
      cy.contains('Invalid email or password').should('be.visible')
    })

    it('navigates to register page', () => {
      cy.visit('/login')
      cy.contains('a', 'Subscribe').click()
      cy.url().should('include', '/register')
    })

    it('navigates to forgot password page', () => {
      cy.visit('/login')
      cy.contains('a', 'Reset it').click()
      cy.url().should('include', '/reset-password')
    })
  })

  describe('Register', () => {
    it('shows validation errors for empty form', () => {
      cy.visit('/register')
      cy.contains('button', 'Create account').click()
      cy.contains('Full name is required').should('be.visible')
      cy.contains('Email is required').should('be.visible')
      cy.contains('Password is required').should('be.visible')
    })

    it('shows error for short password', () => {
      cy.visit('/register')
      cy.get('#register-name').type('Test')
      cy.get('#register-email').type('test@example.com')
      cy.get('#register-password').type('short')
      cy.get('#register-confirm').type('short')
      cy.contains('button', 'Create account').click()
      cy.contains('Password must be at least 8 characters').should('be.visible')
    })

    it('shows error for mismatched passwords', () => {
      cy.visit('/register')
      cy.get('#register-name').type('Test')
      cy.get('#register-email').type('test@example.com')
      cy.get('#register-password').type('password123')
      cy.get('#register-confirm').type('different123')
      cy.contains('button', 'Create account').click()
      cy.contains('Passwords do not match').should('be.visible')
    })

    it('registers successfully and redirects to onboarding', () => {
      cy.intercept('POST', '**/auth/register', {
        statusCode: 200,
        body: mockAuthResponse
      }).as('register')

      cy.visit('/register')
      cy.get('#register-name').type('Test User')
      cy.get('#register-email').type('test@example.com')
      cy.get('#register-password').type('password123')
      cy.get('#register-confirm').type('password123')
      cy.contains('button', 'Create account').click()

      cy.wait('@register')
      cy.url().should('include', '/onboarding')
    })

    it('shows server error on duplicate email', () => {
      cy.intercept('POST', '**/auth/register', {
        statusCode: 409,
        body: { message: 'Email already exists' }
      }).as('registerFail')

      cy.visit('/register')
      cy.get('#register-name').type('Test User')
      cy.get('#register-email').type('existing@example.com')
      cy.get('#register-password').type('password123')
      cy.get('#register-confirm').type('password123')
      cy.contains('button', 'Create account').click()

      cy.wait('@registerFail')
      cy.contains('Email already exists').should('be.visible')
    })
  })

  describe('Auth guards', () => {
    it('redirects to login when accessing protected route unauthenticated', () => {
      cy.visit('/dashboard/archive')
      cy.url().should('include', '/login')
    })

    it('redirects authenticated user from home to archive', () => {
      // Access token lives in memory only; a persisted `user` plus a stubbed
      // silent POST /auth/refresh re-establishes the session on first navigation.
      stubSession()
      cy.intercept('GET', '**/news/digests*', {
        statusCode: 200,
        body: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 10 }
      }).as('digests')

      cy.visit('/', withAuth())
      cy.wait('@refresh')
      cy.url().should('include', '/dashboard/archive')
    })
  })
})
