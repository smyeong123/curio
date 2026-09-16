import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/services/api'
import type { AuthResponse, LoginResponse, VerifyCodeResponse, ResendCodeResponse, RegisterRequest } from '@/types/user'

interface AuthUser {
  id: string
  email: string
  fullName: string
  isAdmin: boolean
}

// An in-flight email/password login awaiting its emailed verification code.
// Held in memory only (never persisted) and carried from the login view to the
// verify-code view.
interface PendingVerification {
  challengeId: string
  email: string
  attemptsRemaining: number
}

// Corrupt localStorage (interrupted write, manual edit) must degrade to a
// signed-out state, not crash the whole app during store construction.
const safeParse = <T>(raw: string | null): T | null => {
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  // Access token in memory only (not persisted) — refresh token is in httpOnly cookie
  const accessToken = ref<string | null>(null)
  const user = ref<AuthUser | null>(safeParse<AuthUser>(localStorage.getItem('user')))

  // Persisted so the email's "Copy code →" link (which opens a fresh tab) can
  // resume the same login challenge. The challengeId is an opaque token, not a
  // session credential, and is cleared the moment login succeeds or is abandoned.
  const PENDING_KEY = 'pendingVerification'
  const pendingVerification = ref<PendingVerification | null>(
    safeParse<PendingVerification>(localStorage.getItem(PENDING_KEY))
  )
  const setPending = (p: PendingVerification | null) => {
    pendingVerification.value = p
    if (p) localStorage.setItem(PENDING_KEY, JSON.stringify(p))
    else localStorage.removeItem(PENDING_KEY)
  }

  const isAuthenticated = computed(() => !!accessToken.value)

  const toUser = (data: AuthResponse): AuthUser => ({
    id: data.userId,
    email: data.email,
    fullName: data.fullName,
    isAdmin: data.isAdmin
  })

  const setAccessToken = (access: string) => {
    accessToken.value = access
  }

  const setUser = (authUser: AuthUser) => {
    user.value = authUser
    localStorage.setItem('user', JSON.stringify(authUser))
  }

  const clearTokens = () => {
    accessToken.value = null
    user.value = null
    localStorage.removeItem('user')
    setPending(null)
    // Per-user UI state must not follow the next account on this browser (e.g.
    // the Archive beat filter persisting across a logout on a shared machine).
    // Clear every persisted store snapshot, plus the live in-memory filter —
    // the dynamic import avoids a static auth → news → api → auth import cycle.
    try {
      Object.keys(localStorage)
        .filter((key) => key.startsWith('curio:store:'))
        .forEach((key) => localStorage.removeItem(key))
    } catch {
      /* storage unavailable — nothing was persisted anyway */
    }
    // Also wipe the in-memory news/quiz singletons: logout is a client-side
    // route push (no reload), so without this the next account briefly sees the
    // previous user's digests and quiz history until its own fetches resolve.
    void import('./news')
      .then(({ useNewsStore }) => { useNewsStore().reset() })
      .catch(() => {})
    void import('./quiz')
      .then(({ useQuizStore }) => { useQuizStore().reset() })
      .catch(() => {})
  }

  // Step 1: verify the password. Either logs the user in (2FA off) or hands back
  // a VERIFICATION_REQUIRED challenge that the verify-code view completes.
  const login = async (email: string, password: string): Promise<LoginResponse> => {
    const response = await api.auth.login(email, password)
    const data = response.data
    if (data.status === 'AUTHENTICATED' && data.auth) {
      setAccessToken(data.auth.accessToken)
      setUser(toUser(data.auth))
      setPending(null)
    } else {
      setPending({
        challengeId: data.challengeId ?? '',
        email: data.email,
        // Fall back to the default budget rather than 0 if the field is ever absent.
        attemptsRemaining: data.attemptsRemaining ?? 5
      })
    }
    return data
  }

  // Step 2: submit the emailed code. On VERIFIED the session is stored; otherwise
  // the remaining-attempts counter is refreshed so the view can show it.
  const verifyCode = async (code: string): Promise<VerifyCodeResponse> => {
    if (!pendingVerification.value) {
      throw new Error('No login in progress')
    }
    const response = await api.auth.verifyCode(pendingVerification.value.challengeId, code)
    const data = response.data
    if (data.status === 'VERIFIED' && data.auth) {
      setAccessToken(data.auth.accessToken)
      setUser(toUser(data.auth))
      setPending(null)
    } else if (typeof data.attemptsRemaining === 'number' && pendingVerification.value) {
      setPending({ ...pendingVerification.value, attemptsRemaining: data.attemptsRemaining })
    }
    return data
  }

  // Ask for a fresh code for the in-flight challenge.
  const resendCode = async (): Promise<ResendCodeResponse> => {
    if (!pendingVerification.value) {
      throw new Error('No login in progress')
    }
    const response = await api.auth.resendCode(pendingVerification.value.challengeId)
    const data = response.data
    if (typeof data.attemptsRemaining === 'number' && pendingVerification.value) {
      setPending({ ...pendingVerification.value, attemptsRemaining: data.attemptsRemaining })
    }
    return data
  }

  const clearPendingVerification = () => {
    setPending(null)
  }

  const register = async (data: RegisterRequest) => {
    const response = await api.auth.register(data)
    // A session via another flow abandons any in-flight login challenge.
    setPending(null)
    setAccessToken(response.data.accessToken)
    setUser(toUser(response.data))
    return response.data
  }

  const googleLogin = async (token: string) => {
    const response = await api.auth.googleLogin(token)
    setPending(null)
    setAccessToken(response.data.accessToken)
    setUser(toUser(response.data))
    return response.data
  }

  const logout = async () => {
    try {
      await api.auth.logout()
    } finally {
      clearTokens()
    }
  }

  // Refresh via httpOnly cookie (sent automatically by browser)
  // Single-flight: the router's bootstrap refresh and the 401 interceptor can
  // ask for a refresh at the same moment. They MUST share one network call —
  // each successful refresh rotates the cookie, so a second parallel call would
  // present the already-rotated token and get rejected, logging the user out.
  let refreshInFlight: Promise<AuthResponse> | null = null

  const refreshToken = (): Promise<AuthResponse> => {
    if (!refreshInFlight) {
      refreshInFlight = (async () => {
        try {
          const response = await api.auth.refresh()
          setAccessToken(response.data.accessToken)
          setUser(toUser(response.data))
          return response.data
        } catch {
          clearTokens()
          throw new Error('Token refresh failed')
        } finally {
          refreshInFlight = null
        }
      })()
    }
    return refreshInFlight
  }

  // Re-fetch the profile and update the cached role. The router calls this when
  // entering the admin section so a mid-session demotion drops the admin UI at
  // the next admin navigation instead of persisting until token expiry. Fails
  // soft: the backend re-checks ADMIN on every request regardless — this is UX
  // freshness, not the security boundary.
  const syncRole = async () => {
    if (!user.value) return
    try {
      const { data } = await api.user.getProfile()
      if (user.value && user.value.isAdmin !== data.isAdmin) {
        setUser({ ...user.value, isAdmin: data.isAdmin })
      }
    } catch {
      /* keep the cached role — server-side authorization is unaffected */
    }
  }

  // Multi-tab session sync: if user is cleared in another tab, clear here too
  window.addEventListener('storage', (e) => {
    if (e.key === 'user' && e.newValue === null) {
      clearTokens()
    }
  })

  return {
    accessToken,
    user,
    pendingVerification,
    isAuthenticated,
    login,
    verifyCode,
    resendCode,
    clearPendingVerification,
    register,
    googleLogin,
    logout,
    refreshToken,
    syncRole,
    setAccessToken,
    clearTokens
  }
})
