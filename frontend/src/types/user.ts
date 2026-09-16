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

export interface LoginRequest {
  email: string
  password: string
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
  /** Preferred delivery hour 0-23 in the chosen timezone. Null = 8 (08:00). */
  deliveryHour?: number | null
  /** true = timezone auto-follows the device; false = pinned to a chosen zone. */
  timezoneAuto?: boolean
}

/** Optional per-user delivery-time settings sent with a preferences update. */
export interface DeliverySettings {
  timezone?: string | null
  deliveryHour?: number | null
  timezoneAuto?: boolean
}

export interface UpdateProfileRequest {
  fullName?: string
  deliveryEnabled?: boolean
}
