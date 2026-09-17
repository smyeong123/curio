// Digest Studio: the self-serve generate / send console (GET /studio/status is polled).
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

/** The backend is still working on the task; the console polls until it settles. */
export const isTaskActive = (task?: StudioTask) =>
  task?.state === 'QUEUED' || task?.state === 'RUNNING'

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
