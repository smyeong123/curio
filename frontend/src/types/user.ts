export interface User {
  id: string
  email: string
  fullName: string
  isAdmin: boolean
  deliveryEnabled: boolean
  emailVerified: boolean
  createdAt: string
  hasPassword: boolean
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: string
  email: string
  fullName: string
  isAdmin: boolean
}

// Step 1 of email/password login. Either a session is issued directly
// (AUTHENTICATED, when 2FA is off) or a one-time code is emailed (VERIFICATION_REQUIRED).
export interface LoginResponse {
  status: 'VERIFICATION_REQUIRED' | 'AUTHENTICATED'
  challengeId?: string
  email: string
  attemptsRemaining?: number
  expiresInSeconds?: number
  auth?: AuthResponse
}

// Step 2: result of submitting the emailed code.
export interface VerifyCodeResponse {
  status: 'VERIFIED' | 'INVALID_CODE' | 'LOCKED' | 'EXPIRED'
  attemptsRemaining?: number
  message?: string
  resetAvailable: boolean
  auth?: AuthResponse
}

export interface ResendCodeResponse {
  status: 'SENT' | 'EXPIRED'
  attemptsRemaining?: number
  expiresInSeconds?: number
  message?: string
}

export interface PreferencesResponse {
  topics: string[]
  /** IANA timezone id the digest is scheduled in. Null = UTC. */
  timezone?: string | null
  /** Preferred delivery hour 0-23 in the chosen timezone. Null = 6 (06:00). */
  deliveryHour?: number | null
  /** true = timezone auto-follows the device; false = pinned to a chosen zone. */
  timezoneAuto?: boolean
  /** Edition the digest, quiz and daily email are written in. Defaults to "en". */
  language?: DigestLanguage
}

/** Edition codes the backend accepts — the same codes as the UI locale. */
export type DigestLanguage = 'en' | 'ko'

/** Optional per-user delivery-time settings sent with a preferences update. */
export interface DeliverySettings {
  timezone?: string | null
  deliveryHour?: number | null
  timezoneAuto?: boolean
  /** Edition for the digest, quiz and email; omit to leave it unchanged. */
  language?: DigestLanguage
}

export interface UpdateProfileRequest {
  fullName?: string
  deliveryEnabled?: boolean
}

/** BYOK API-key summary as returned by list/save — the key itself is never returned. */
export interface ApiKeySummary {
  provider: 'CLAUDE' | 'GEMINI' | 'OPENAI'
  keyPreview: string
  validated: boolean
  validatedAt: string | null
  lastUsedAt: string | null
  updatedAt: string
}
