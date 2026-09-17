import type { User } from '@/types/user'

// Newsroom (admin) API shapes — one place, so a backend field rename is a
// vue-tsc error rather than a runtime surprise in whichever view read it.

export interface AdminStats {
  totalUsers: number
  emailsSentToday: number
  quizCompletionsToday: number
}

/** Subscriber row in the reader roster (GET /admin/users). */
export interface AdminUser {
  id: string
  email: string
  fullName: string | null
  topicsCount: number
  deliveryEnabled: boolean
  createdAt: string
}

/** GET /admin/users/:id */
export interface AdminUserDetail {
  user: User
  topics: string[]
  metrics: {
    digestCount: number
    quizAttempts: number
    averageQuizScore: number | null
  }
  recentDigests: { id: string; generatedAt: string }[]
  recentQuizAttempts: { id: string; completedAt: string; score: number }[]
}

/** Row in the full-corpus digest browser (GET /admin/digests). */
export interface AdminDigest {
  id: string
  userId: string
  userEmail: string
  userFullName: string | null
  content?: { generatedFor?: string[]; summaries?: Array<{ topic?: string }> } | null
  generatedAt: string
  emailSentAt: string | null
}

/** Per-topic subscriber + generation status (GET /admin/topics/status). */
export interface TopicStatus {
  topic: string
  subscriberCount: number
  latestDigestGeneratedAt: string | null
  digestsGeneratedToday: number
}

export interface AuditLogEntry {
  id: number
  actorId: string | null
  actorEmail: string
  action: string
  targetType: string | null
  targetId: string | null
  requestId: string | null
  metadata: Record<string, unknown> | null
  createdAt: string
}

/** One sampled per-user failure from a digest batch. */
export interface JobSampleError {
  userEmail: string
  message: string
}

/**
 * Result payload of the last run, as stored by the backend JobStatusRegistry.
 * One shape per job whether cron or "Run now" triggered it; every counter is
 * optional because the registry stores a plain map.
 */
export interface JobResult {
  /** Set client-side when the registry reports FAILED (there is no result body then). */
  failed?: boolean
  // Batch bookkeeping, present on digest and email runs
  durationMs?: number
  /** Users the run attempted — for the email job, those past their delivery hour. */
  usersProcessed?: number
  /** Every delivery-enabled user the run paged through. */
  usersScanned?: number
  chunks?: number
  chunkSize?: number
  sampleErrors?: JobSampleError[]
  errorsByType?: Record<string, number>
  // Digest runs
  digestSuccess?: number
  digestFail?: number
  digestSkipped?: number
  quizSuccess?: number
  quizFail?: number
  /** Only present when the run was narrowed to specific beats. */
  topicFilter?: string[]
  // Email runs
  sentCount?: number
  failCount?: number
  // Cleanup
  deletedDigests?: number
  cutoffDate?: string
}

/** What a job card shows: the live flag plus the registry's last run. */
export type JobCardState = Pick<JobStatusEntry, 'running' | 'lastRanAt' | 'lastResult'>

/** GET /admin/jobs/status entry. */
export interface JobStatusEntry {
  jobName: 'digest-generation' | 'email-send' | 'cleanup'
  lastRanAt: string | null
  lastStatus: 'SUCCESS' | 'FAILED' | 'NEVER_RAN'
  lastResult: JobResult | null
  running: boolean
}

/** 202 body of the fire-and-forget batch triggers. */
export interface JobTriggerResponse {
  status: 'started' | 'already_running'
}

/** Synchronous cleanup result (POST /admin/cleanup). */
export interface CleanupResult {
  deletedDigests: number
  cutoffDate: string
}
