import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import type { AuthResponse, LoginResponse, VerifyCodeResponse, ResendCodeResponse, RegisterRequest, UpdateProfileRequest, PreferencesResponse, DeliverySettings } from '@/types/user'
import type { Digest, DigestPage } from '@/types/news'
import type { Quiz, QuizSubmitResponse, QuizHistoryPage } from '@/types/quiz'

// ── Digest Studio types ────────────────────────────────────────────
export type StudioTaskState = 'IDLE' | 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'SKIPPED' | 'FAILED'

export interface StudioTask {
  type: 'digest' | 'email'
  state: StudioTaskState
  phase?: string | null
  current?: number
  total?: number
  message?: string
  startedAt?: string
  finishedAt?: string
  updatedAt?: string
  digestId?: string
}

export interface StudioOverview {
  email: string
  topics: string[]
  topicCount: number
  latestDigest: { id: string; generatedAt: string | null; emailSentAt: string | null } | null
  hasUnsentDigest: boolean
}

export interface StudioStatus {
  digest: StudioTask
  email: StudioTask
  overview: StudioOverview
}

// ── BYOK API-key summary (server shape returned by list/save) ───────
export interface ApiKeySummary {
  provider: 'CLAUDE' | 'GEMINI' | 'OPENAI'
  keyPreview: string
  validated: boolean
  validatedAt: string | null
  lastUsedAt: string | null
  updatedAt: string
}

const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  withCredentials: true, // Send httpOnly cookies with requests
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor for JWT
apiClient.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.accessToken) {
    config.headers.Authorization = `Bearer ${authStore.accessToken}`
  }
  return config
})

// Response interceptor for token refresh — with infinite-loop guard
let isRefreshing = false
let failedQueue: Array<{
  resolve: (value: unknown) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: unknown) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve(undefined)
    }
  })
  failedQueue = []
}

// Redact the bearer token (and any cookie) from a rejected request's config so
// the access token can't ride the error object into the console, a toast, or a
// future Sentry breadcrumb. The retry path re-adds Authorization via the request
// interceptor from the in-memory store, so scrubbing here is safe.
const scrubSensitiveHeaders = (error: unknown) => {
  const headers = (error as { config?: { headers?: Record<string, unknown> } })?.config?.headers
  if (headers) {
    delete headers.Authorization
    delete headers.authorization
    delete headers.Cookie
    delete headers.cookie
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    scrubSensitiveHeaders(error)
    const originalRequest = error.config

    // Only attempt refresh for 401s on non-auth endpoints, and only once per request
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/')
    ) {
      if (isRefreshing) {
        // Another refresh is in progress — queue this request. Mark it retried so
        // that if the replayed request still 401s it rejects instead of kicking
        // off yet another refresh round.
        originalRequest._retry = true
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(() => apiClient.request(originalRequest))
      }

      originalRequest._retry = true
      isRefreshing = true

      const authStore = useAuthStore()
      try {
        await authStore.refreshToken()
        processQueue(null)
        return apiClient.request(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError)
        authStore.clearTokens()
        // Preserve where the user was headed so login can bounce them back.
        const from = router.currentRoute.value.fullPath
        router.push(
          from && from !== '/' && !from.startsWith('/login')
            ? { name: 'login', query: { redirect: from } }
            : { name: 'login' }
        )
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error)
  }
)

export const api = {
  auth: {
    login: (email: string, password: string) =>
      apiClient.post<LoginResponse>('/auth/login', { email, password }),
    verifyCode: (challengeId: string, code: string) =>
      apiClient.post<VerifyCodeResponse>('/auth/verify-code', { challengeId, code }),
    resendCode: (challengeId: string) =>
      apiClient.post<ResendCodeResponse>('/auth/resend-code', { challengeId }),
    register: (data: RegisterRequest) =>
      apiClient.post<AuthResponse>('/auth/register', data),
    googleLogin: (token: string) =>
      apiClient.post<AuthResponse>('/auth/google', { token }),
    forgotPassword: (email: string) =>
      apiClient.post('/auth/forgot-password', { email }),
    resetPassword: (token: string, newPassword: string) =>
      apiClient.post('/auth/reset-password', { token, newPassword }),
    refresh: () =>
      // Tight timeout: the silent refresh gates the very first route render on
      // hard reloads. On a dead/slow network the user must fall through to the
      // login screen in seconds, not stare at the boot splash for the 30 s
      // global default.
      apiClient.post<AuthResponse>('/auth/refresh', {}, { timeout: 8_000 }),
    logout: () =>
      apiClient.post('/auth/logout', {}),
  },
  user: {
    getProfile: () => apiClient.get<import('@/types/user').User>('/user/me'),
    updateProfile: (data: UpdateProfileRequest) =>
      apiClient.put<import('@/types/user').User>('/user/me', data),
    getPreferences: () => apiClient.get<PreferencesResponse>('/user/preferences'),
    updatePreferences: (topics: string[], delivery?: DeliverySettings) =>
      apiClient.put<PreferencesResponse>('/user/preferences', { topics, ...(delivery ?? {}) }),
    deleteAccount: () => apiClient.delete('/user/me'),
    changePassword: (currentPassword: string, newPassword: string) =>
      apiClient.put('/user/me/password', { currentPassword, newPassword }),
  },
  apiKeys: {
    list: () => apiClient.get<ApiKeySummary[]>('/user/api-keys'),
    save: (provider: 'CLAUDE' | 'GEMINI' | 'OPENAI', apiKey: string, currentPassword: string) =>
      // Validation hits the LLM provider — give it room. Keys are always
      // validated server-side (the old opt-out flag was removed).
      apiClient.post<ApiKeySummary>('/user/api-keys', { provider, apiKey, currentPassword },
        { timeout: 30_000 }),
    delete: (provider: 'CLAUDE' | 'GEMINI' | 'OPENAI', currentPassword: string) =>
      apiClient.delete(`/user/api-keys/${provider}`, { data: { currentPassword } }),
    validate: (provider: 'CLAUDE' | 'GEMINI' | 'OPENAI', apiKey: string) =>
      apiClient.post<{ valid: boolean }>('/user/api-keys/validate',
        { provider, apiKey }, { timeout: 30_000 }),
  },
  news: {
    getDigests: (page = 0, size = 10) =>
      apiClient.get<DigestPage>(`/news/digests?page=${page}&size=${size}`),
    getDigest: (id: string) =>
      apiClient.get<Digest>(`/news/digests/${id}`),
    // Full-text search over the user's whole retained archive (server-side FTS).
    search: (q: string, page = 0, size = 50) =>
      apiClient.get<DigestPage>('/news/search', { params: { q, page, size } }),
  },
  quiz: {
    // Quiz generation can take ~15s on cold cache (Claude latency).
    getQuiz: (digestId: string) =>
      apiClient.get<Quiz>(`/quiz/digest/${digestId}`, { timeout: 120_000 }),
    submitQuiz: (quizId: string, answers: Record<number, string>) =>
      apiClient.post<QuizSubmitResponse>(`/quiz/${quizId}/submit`, { answers }),
    getHistory: (page = 0) =>
      apiClient.get<QuizHistoryPage>(`/quiz/history?page=${page}`),
  },
  studio: {
    // Self-serve digest console for the current user. generate/sendEmail kick
    // off background tasks (202) and return the initial status; getStatus is
    // polled for live progress.
    getStatus: () =>
      apiClient.get<StudioStatus>('/studio/status'),
    generate: () =>
      apiClient.post<StudioTask>('/studio/generate'),
    sendEmail: () =>
      apiClient.post<StudioTask>('/studio/send-email'),
  },
  admin: {
    getUsers: (page = 0, search = '') =>
      apiClient.get('/admin/users', { params: { page, search } }),
    getUser: (id: string) =>
      apiClient.get(`/admin/users/${id}`),
    getStats: () =>
      apiClient.get('/admin/stats'),
    getTopicDistribution: () =>
      apiClient.get('/admin/stats/topics'),
    getTopicsStatus: () =>
      apiClient.get('/admin/topics/status'),
    getDigests: (params: { page?: number; size?: number; topic?: string; userEmail?: string } = {}) =>
      apiClient.get('/admin/digests', { params }),
    // Admin batches iterate over all subscribers and serially hit the AI
    // provider / email vendor; expect minutes, not seconds.
    generateDigests: (topics?: string[]) =>
      apiClient.post('/admin/generate-digests', topics ? { topics } : {}, { timeout: 600_000 }),
    sendEmails: () =>
      apiClient.post('/admin/send-emails', undefined, { timeout: 600_000 }),
    runCleanup: () =>
      apiClient.post('/admin/cleanup', undefined, { timeout: 60_000 }),
    getJobsStatus: () =>
      apiClient.get('/admin/jobs/status'),
    getAuditLog: (page = 0, size = 20) =>
      apiClient.get('/admin/audit-log', { params: { page, size } }),
  },
}

export default apiClient
