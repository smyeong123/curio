import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/services/api', () => ({
  api: {
    auth: {
      login: vi.fn(),
      verifyCode: vi.fn(),
      resendCode: vi.fn(),
      register: vi.fn(),
      googleLogin: vi.fn(),
      logout: vi.fn(),
      refresh: vi.fn()
    }
  }
}))

import { api } from '@/services/api'

// Refresh tokens live in an httpOnly cookie, not in the response body. The
// store only hydrates accessToken + user from the response.
const mockAuthResponse = {
  data: {
    accessToken: 'access-123',
    userId: 'user-1',
    email: 'test@example.com',
    fullName: 'Test User',
    isAdmin: false
  }
} as any

const sessionPayload = {
  accessToken: 'access-123',
  userId: 'user-1',
  email: 'test@example.com',
  fullName: 'Test User',
  isAdmin: false
}

// Email/password login is two-step. AUTHENTICATED comes back only when the
// emailed-code step is disabled server-side.
const authenticatedLogin = { data: { status: 'AUTHENTICATED', email: 'test@example.com', auth: sessionPayload } } as any
const challengeLogin = {
  data: { status: 'VERIFICATION_REQUIRED', challengeId: 'ch-1', email: 'test@example.com', attemptsRemaining: 5 }
} as any
const verifiedCode = { data: { status: 'VERIFIED', resetAvailable: false, auth: sessionPayload } } as any

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('starts unauthenticated when no token in storage', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.user).toBeNull()
  })

  it('login with a required code stores a pending challenge and does not authenticate', async () => {
    vi.mocked(api.auth.login).mockResolvedValue(challengeLogin)
    const store = useAuthStore()

    const result = await store.login('test@example.com', 'password')

    expect(result.status).toBe('VERIFICATION_REQUIRED')
    expect(store.isAuthenticated).toBe(false)
    expect(store.accessToken).toBeNull()
    expect(store.pendingVerification).toEqual({
      challengeId: 'ch-1',
      email: 'test@example.com',
      attemptsRemaining: 5
    })
  })

  it('login completes immediately when verification is disabled', async () => {
    vi.mocked(api.auth.login).mockResolvedValue(authenticatedLogin)
    const store = useAuthStore()

    await store.login('test@example.com', 'password')

    expect(store.accessToken).toBe('access-123')
    expect(store.user).toEqual({
      id: 'user-1',
      email: 'test@example.com',
      fullName: 'Test User',
      isAdmin: false
    })
    expect(store.isAuthenticated).toBe(true)
    expect(store.pendingVerification).toBeNull()
  })

  it('verifyCode stores the session and clears the pending challenge', async () => {
    vi.mocked(api.auth.login).mockResolvedValue(challengeLogin)
    vi.mocked(api.auth.verifyCode).mockResolvedValue(verifiedCode)
    const store = useAuthStore()

    await store.login('test@example.com', 'password')
    const result = await store.verifyCode('123456')

    expect(result.status).toBe('VERIFIED')
    expect(api.auth.verifyCode).toHaveBeenCalledWith('ch-1', '123456')
    expect(store.accessToken).toBe('access-123')
    expect(store.isAuthenticated).toBe(true)
    expect(store.pendingVerification).toBeNull()
  })

  it('verifyCode refreshes the remaining-attempts count on a wrong code', async () => {
    vi.mocked(api.auth.login).mockResolvedValue(challengeLogin)
    vi.mocked(api.auth.verifyCode).mockResolvedValue({
      data: { status: 'INVALID_CODE', resetAvailable: false, attemptsRemaining: 4, message: 'nope' }
    } as any)
    const store = useAuthStore()

    await store.login('test@example.com', 'password')
    const result = await store.verifyCode('000000')

    expect(result.status).toBe('INVALID_CODE')
    expect(store.isAuthenticated).toBe(false)
    expect(store.pendingVerification?.attemptsRemaining).toBe(4)
  })

  it('register sets access token and user', async () => {
    vi.mocked(api.auth.register).mockResolvedValue(mockAuthResponse)
    const store = useAuthStore()

    await store.register({ email: 'test@example.com', password: 'password', fullName: 'Test User' })

    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.email).toBe('test@example.com')
  })

  it('googleLogin sets access token and user', async () => {
    vi.mocked(api.auth.googleLogin).mockResolvedValue(mockAuthResponse)
    const store = useAuthStore()

    await store.googleLogin('google-token')

    expect(store.isAuthenticated).toBe(true)
    expect(api.auth.googleLogin).toHaveBeenCalledWith('google-token')
  })

  it('logout clears tokens even if API fails', async () => {
    vi.mocked(api.auth.login).mockResolvedValue(authenticatedLogin)
    vi.mocked(api.auth.logout).mockRejectedValue(new Error('network'))
    const store = useAuthStore()

    await store.login('test@example.com', 'password')
    expect(store.isAuthenticated).toBe(true)

    await store.logout().catch(() => {})

    expect(store.accessToken).toBeNull()
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.removeItem).toHaveBeenCalledWith('user')
  })

  it('refreshToken updates access token on success', async () => {
    vi.mocked(api.auth.refresh).mockResolvedValue({
      data: { ...mockAuthResponse.data, accessToken: 'new-access' }
    } as any)

    const store = useAuthStore()
    await store.refreshToken()

    expect(store.accessToken).toBe('new-access')
    expect(store.isAuthenticated).toBe(true)
  })

  it('refreshToken throws when the refresh call fails', async () => {
    vi.mocked(api.auth.refresh).mockRejectedValue(new Error('no cookie'))
    const store = useAuthStore()

    await expect(store.refreshToken()).rejects.toThrow('Token refresh failed')
    expect(store.accessToken).toBeNull()
  })

  it('clearTokens resets all state', () => {
    const store = useAuthStore()
    store.setAccessToken('token')
    // @ts-expect-error user is a ref exposed by the store setup
    store.user = { id: '1', email: 'a', fullName: 'b', isAdmin: false }

    store.clearTokens()

    expect(store.accessToken).toBeNull()
    expect(store.user).toBeNull()
  })
})
