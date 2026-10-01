import { stubSession, withAuth, mockUser } from './helpers'

const mockProfile = {
  id: mockUser.id,
  email: mockUser.email,
  fullName: mockUser.fullName,
  isAdmin: false,
  deliveryEnabled: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
  hasPassword: true
}

// timezoneAuto: false (pinned zone) keeps the timezone select enabled AND stops
// DashboardLayout's useEnsureTimezone from firing a background PUT /user/preferences
// when the mocked zone differs from the test browser's device zone.
const mockPreferences = {
  topics: ['Claude (Anthropic)', 'GPT & ChatGPT (OpenAI)', 'Pricing & Availability'],
  deliveryHour: 6,
  timezone: 'Asia/Seoul',
  timezoneAuto: false
}

describe('Settings', () => {
  beforeEach(() => {
    stubSession()

    cy.intercept('GET', '**/user/me', {
      statusCode: 200,
      body: mockProfile
    }).as('getProfile')

    cy.intercept('GET', '**/user/preferences', {
      statusCode: 200,
      body: mockPreferences
    }).as('getPreferences')

    // ApiKeyManager (BYOK section) lists keys on mount.
    cy.intercept('GET', '**/user/api-keys', {
      statusCode: 200,
      body: []
    }).as('getApiKeys')
  })

  const visitSettings = () => {
    cy.visit('/dashboard/settings', withAuth())
    cy.wait('@getProfile')
    cy.wait('@getPreferences')
  }

  it('loads profile and delivery settings into the form', () => {
    visitSettings()

    cy.contains('settings').should('be.visible') // "Your settings." masthead
    cy.contains(mockProfile.email).should('be.visible')
    cy.get('#settings-name').should('have.value', 'Test User')

    // Delivery: enabled, 06:00, pinned to Asia/Seoul (auto-timezone off).
    cy.get('[role="switch"]').should('have.attr', 'aria-checked', 'true')
    cy.get('#settings-delivery-hour').should('have.value', '6')
    cy.get('#settings-timezone').should('have.value', 'Asia/Seoul').and('not.be.disabled')
    cy.contains("Automatically use my device's timezone")
      .parent()
      .find('input[type="checkbox"]')
      .should('not.be.checked')
  })

  it('updates profile with new name and delivery time settings', () => {
    cy.intercept('PUT', '**/user/me', {
      statusCode: 200,
      body: { ...mockProfile, fullName: 'Updated Name' }
    }).as('updateProfile')

    cy.intercept('PUT', '**/user/preferences', {
      statusCode: 200,
      body: { ...mockPreferences, deliveryHour: 9, timezone: 'Europe/London' }
    }).as('updatePrefs')

    visitSettings()

    // The page transition's @after-enter moves focus to the h1 (a11y helper in
    // DashboardLayout). Wait for that before typing, or it steals focus mid-type
    // and the remaining keystrokes land on the heading.
    cy.get('h1').should('have.focus')
    cy.get('#settings-name').should('have.value', 'Test User')
    cy.get('#settings-name').clear().type('Updated Name')
    cy.get('#settings-name').should('have.value', 'Updated Name')
    cy.get('#settings-delivery-hour').select('09:00')
    cy.get('#settings-timezone').select('Europe/London')
    cy.contains('button', 'Save changes').click()

    cy.wait('@updateProfile').its('request.body').should('deep.include', {
      fullName: 'Updated Name',
      deliveryEnabled: true
    })
    // Delivery settings persist against the last-saved topic list.
    cy.wait('@updatePrefs').its('request.body').should('deep.equal', {
      topics: mockPreferences.topics,
      deliveryHour: 9,
      timezone: 'Europe/London',
      timezoneAuto: false
    })
    cy.contains('Profile updated').should('be.visible')
  })

  it('enabling auto-timezone disables the zone select and saves timezoneAuto=true (V26)', () => {
    cy.intercept('PUT', '**/user/me', { statusCode: 200, body: mockProfile }).as('updateProfile')
    cy.intercept('PUT', '**/user/preferences', {
      statusCode: 200,
      body: { ...mockPreferences, timezoneAuto: true }
    }).as('updatePrefs')

    visitSettings()

    // Pinned state loaded (auto off, select enabled) — now flip auto-follow ON.
    cy.get('#settings-timezone').should('not.be.disabled')
    cy.contains("Automatically use my device's timezone")
      .parent()
      .find('input[type="checkbox"]')
      .check()
    cy.get('#settings-timezone').should('be.disabled')

    cy.contains('button', 'Save changes').click()
    cy.wait('@updateProfile')
    // Auto mode persists the DEVICE zone (whatever the test browser reports),
    // not the previously pinned one — assert the flag plus a real IANA string.
    cy.wait('@updatePrefs').its('request.body').then((body) => {
      expect(body.timezoneAuto).to.equal(true)
      expect(body.timezone).to.be.a('string').and.to.have.length.greaterThan(0)
      expect(body.deliveryHour).to.equal(6)
    })
  })

  it('toggles email delivery off and hides delivery time controls', () => {
    visitSettings()

    cy.get('#settings-delivery-hour').should('exist')
    cy.get('[role="switch"]').click()
    cy.get('[role="switch"]').should('have.attr', 'aria-checked', 'false')
    cy.get('#settings-delivery-hour').should('not.exist')
    cy.get('#settings-timezone').should('not.exist')
  })

  it('shows selected beats count and per-section counts', () => {
    visitSettings()

    cy.contains('3 selected').should('be.visible')
    // 2 of the mocked beats live under Frontier Labs, 1 under Capabilities & Ecosystem.
    cy.contains('button', 'Frontier Labs').should('contain', '2 on')
    cy.contains('button', 'Capabilities & Ecosystem').should('contain', '1 on')
  })

  it('adds a beat and updates preferences', () => {
    cy.intercept('PUT', '**/user/preferences', {
      statusCode: 200,
      body: { ...mockPreferences, topics: [...mockPreferences.topics, 'Reasoning & Context'] }
    }).as('updatePrefs')

    visitSettings()

    // Expand the Capabilities & Ecosystem accordion, then add a beat.
    cy.contains('Capabilities & Ecosystem').click()
    cy.contains('Model Capabilities').should('be.visible')
    cy.contains('button', 'Reasoning & Context').click()
    cy.contains('4 selected').should('be.visible')

    cy.contains('button', 'Update preferences').click()

    cy.wait('@updatePrefs')
      .its('request.body.topics')
      .should('include', 'Reasoning & Context')
      .and('have.length', 4)
    cy.contains('Preferences updated').should('be.visible')
  })

  it('shows server error message when preferences update fails', () => {
    cy.intercept('PUT', '**/user/preferences', {
      statusCode: 400,
      body: { message: 'At least 3 topics required' }
    }).as('updatePrefsFail')

    visitSettings()

    cy.contains('button', 'Update preferences').click()
    cy.wait('@updatePrefsFail')
    cy.contains('At least 3 topics required').should('be.visible')
  })

  it('shows error toast when profile update fails', () => {
    cy.intercept('PUT', '**/user/me', {
      statusCode: 500,
      body: { message: 'Server error' }
    }).as('updateProfileFail')

    visitSettings()

    cy.contains('button', 'Save changes').click()
    cy.wait('@updateProfileFail')
    // The UI surfaces the server's message field when present (generic
    // 'Failed to update profile' is only the fallback for message-less errors).
    cy.contains('Server error').should('be.visible')
  })

  it('shows error toast when settings fail to load', () => {
    cy.intercept('GET', '**/user/me', {
      statusCode: 500,
      body: { message: 'Server error' }
    }).as('getProfileFail')

    cy.visit('/dashboard/settings', withAuth())
    cy.wait('@getProfileFail')
    cy.contains('Failed to load settings').should('be.visible')
  })

  it('opens and closes the delete account modal', () => {
    visitSettings()

    cy.contains('button', 'Delete my account').click()
    cy.contains('Are you sure you want to delete your account?').should('be.visible')
    cy.contains('button', 'Cancel').click()
    cy.contains('Are you sure you want to delete your account?').should('not.exist')
  })

  it('deletes account and redirects to home', () => {
    cy.intercept('DELETE', '**/user/me', {
      statusCode: 200,
      body: {}
    }).as('deleteAccount')

    visitSettings()

    cy.contains('button', 'Delete my account').click()
    cy.get('[role="dialog"]').contains('button', 'Delete my account').click()

    cy.wait('@deleteAccount')
    cy.contains('Account deleted').should('be.visible')
    cy.location('pathname').should('eq', '/')
  })
})
