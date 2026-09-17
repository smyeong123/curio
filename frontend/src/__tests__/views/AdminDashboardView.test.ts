import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AdminDashboardView from '@/views/admin/AdminDashboardView.vue'
import { ALL_TOPICS } from '@/data/topics'
import type { JobStatusEntry } from '@/types/admin'

vi.mock('@/services/api', () => ({
  api: {
    admin: {
      getStats: vi.fn(),
      getTopicsStatus: vi.fn(),
      getJobsStatus: vi.fn(),
      generateDigests: vi.fn(),
      sendEmails: vi.fn(),
      runCleanup: vi.fn(),
    },
  },
}))

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }))
vi.mock('@/composables/useToast', () => ({ useToast: () => toast }))

import { api } from '@/services/api'

const mockedStats = vi.mocked(api.admin.getStats)
const mockedTopics = vi.mocked(api.admin.getTopicsStatus)
const mockedJobs = vi.mocked(api.admin.getJobsStatus)
const mockedGenerate = vi.mocked(api.admin.generateDigests)

const job = (jobName: JobStatusEntry['jobName'], overrides: Partial<JobStatusEntry> = {}): JobStatusEntry => ({
  jobName,
  lastRanAt: null,
  lastStatus: 'NEVER_RAN',
  lastResult: null,
  running: false,
  ...overrides,
})

const neverRan = () => [job('digest-generation'), job('email-send'), job('cleanup')]

const jobsResponse = (jobs: JobStatusEntry[]) => ({ data: jobs }) as never

const mountDashboard = async () => {
  const w = mount(AdminDashboardView)
  await flushPromises()
  return w
}

// Buttons render in card order: generate, send, cleanup (the modal is closed).
const runNowButton = (w: ReturnType<typeof mount>, index: number) => w.findAll('button')[index]!

const runningBadge = (w: ReturnType<typeof mount>) => w.find('span.animate-pulse')

// Only timers are faked so test-utils' flushPromises (setImmediate) keeps working.
const useJobTimers = () => vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] })

describe('AdminDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedStats.mockResolvedValue({ data: { totalUsers: 1234, emailsSentToday: 16, quizCompletionsToday: 9 } } as never)
    mockedTopics.mockResolvedValue({
      data: [
        { topic: 'Claude (Anthropic)', subscriberCount: 20, latestDigestGeneratedAt: '2026-09-16T12:00:00Z', digestsGeneratedToday: 3 },
        { topic: 'Reasoning & Context', subscriberCount: 15, latestDigestGeneratedAt: null, digestsGeneratedToday: 0 },
      ],
    } as never)
    mockedJobs.mockResolvedValue(jobsResponse(neverRan()))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the quick stats, the three job cards with their cron lines and the beats table', async () => {
    const w = await mountDashboard()
    const text = w.text()
    expect(w.get('h1').text()).toBe('The desk.')
    expect(text).toContain('1,234')
    expect(text).toContain('Total subscribers')

    expect(text).toContain('Generate digests')
    expect(text).toContain('Cron · 06:00 UTC')
    expect(text).toContain('Send the morning post')
    expect(text).toContain('Cron · hourly (per-user tz)')
    expect(text).toContain('Recycle old issues')
    expect(text).toContain('Cron · 00:00 UTC')
    expect(w.findAll('article')).toHaveLength(3)
    expect(runNowButton(w, 0).text()).toContain('Run now')
    expect(runNowButton(w, 2).text()).toContain('Run now')

    // Never-ran jobs show neither a badge nor a "Last ran" line.
    expect(runningBadge(w).exists()).toBe(false)
    expect(text).not.toContain('Last ran')

    expect(text).toContain('2 beats')
    expect(text).toContain('Claude (Anthropic)')
    expect(text).toContain('Reasoning & Context')
    expect(text).toContain('Sep 16')
  })

  it('shows the Running… badge and disables Run now while the server reports a job in flight', async () => {
    mockedJobs.mockResolvedValue(jobsResponse([job('digest-generation', { running: true }), job('email-send'), job('cleanup')]))
    const w = await mountDashboard()

    expect(runningBadge(w).exists()).toBe(true)
    expect(runNowButton(w, 0).text()).toContain('Running…')
    expect(runNowButton(w, 0).attributes('disabled')).toBeDefined()
    expect(runNowButton(w, 1).attributes('disabled')).toBeUndefined()
    w.unmount()
  })

  it('shows the failed badge and message when the last run failed', async () => {
    mockedJobs.mockResolvedValue(
      jobsResponse([job('digest-generation'), job('email-send', { lastStatus: 'FAILED', lastRanAt: '2026-09-16T12:00:00Z' }), job('cleanup')])
    )
    const w = await mountDashboard()
    const text = w.text()
    expect(text).toContain('● Failed')
    expect(text).toContain('Job failed — check server logs')
    expect(text).toContain('Last ran · Sep 16, 2026')
  })

  it('renders the digest result with the Filed badge and the skipped variant', async () => {
    mockedJobs.mockResolvedValue(
      jobsResponse([
        job('digest-generation', {
          lastStatus: 'SUCCESS',
          lastRanAt: '2026-09-16T12:00:00Z',
          lastResult: { usersProcessed: 5, usersScanned: 5, digestSuccess: 4, digestFail: 0, digestSkipped: 1, quizSuccess: 4, quizFail: 0, topicFilter: [ALL_TOPICS[0]], durationMs: 12345 },
        }),
        job('email-send'),
        job('cleanup'),
      ])
    )
    const w = await mountDashboard()
    const text = w.text()
    expect(text).toContain('● Filed')
    expect(text).toContain('4 / 5 users · 0 failed · 1 skipped')
    expect(text).toContain('Quizzes · 4 filed · 0 failed · Beats · ')
    expect(text).toContain(' · Took 12.3 sec')
    expect(text).not.toContain('Why it failed')
  })

  it('renders the plain digest result and explains the failures', async () => {
    mockedJobs.mockResolvedValue(
      jobsResponse([
        job('digest-generation', {
          lastStatus: 'SUCCESS',
          lastRanAt: '2026-09-16T12:00:00Z',
          lastResult: {
            usersProcessed: 10,
            digestSuccess: 9,
            digestFail: 1,
            digestSkipped: 0,
            errorsByType: { TimeoutException: 1 },
            sampleErrors: [{ userEmail: 'reader@example.com', message: 'provider timed out' }],
          },
        }),
        job('email-send'),
        job('cleanup'),
      ])
    )
    const w = await mountDashboard()
    const text = w.text()
    expect(text).toContain('9 / 10 users · 1 failed')
    expect(text).toContain('Why it failed')
    expect(text).toContain('TimeoutException × 1')
    expect(text).toContain('reader@example.com')
    expect(text).toContain('provider timed out')
    expect(w.find('[role="alert"]').exists()).toBe(true)
  })

  it('renders the email and cleanup result lines', async () => {
    mockedJobs.mockResolvedValue(
      jobsResponse([
        job('digest-generation'),
        job('email-send', { lastStatus: 'SUCCESS', lastRanAt: '2026-09-16T12:00:00Z', lastResult: { sentCount: 7, failCount: 0, usersProcessed: 7, usersScanned: 12, durationMs: 4100 } }),
        job('cleanup', { lastStatus: 'SUCCESS', lastRanAt: '2026-09-16T12:00:00Z', lastResult: { deletedDigests: 3, cutoffDate: '2026-08-17T12:00:00Z' } }),
      ])
    )
    const w = await mountDashboard()
    const text = w.text()
    expect(text).toContain('● Delivered')
    expect(text).toContain('7 sent, 0 failed (12 users)')
    expect(text).toContain('7 due this hour · Took 4.1 sec')
    expect(text).toContain('● Recycled')
    expect(text).toContain('3 digests recycled (cutoff: Aug 17')
  })

  it('runs digest generation, toasts, and polls until the registry records the run', async () => {
    useJobTimers()
    mockedGenerate.mockResolvedValue({ data: { status: 'started' } } as never)
    const w = await mountDashboard()

    await runNowButton(w, 0).trigger('click')
    await flushPromises()
    expect(mockedGenerate).toHaveBeenCalledWith(undefined)
    expect(toast.success).toHaveBeenCalledWith('Digest generation started — counts will update below')
    expect(runningBadge(w).exists()).toBe(true)
    expect(runNowButton(w, 0).attributes('disabled')).toBeDefined()

    // First poll: still running on the server.
    mockedJobs.mockResolvedValue(jobsResponse([job('digest-generation', { running: true }), job('email-send'), job('cleanup')]))
    await vi.advanceTimersByTimeAsync(3000)
    expect(mockedJobs).toHaveBeenCalledTimes(2)
    expect(runningBadge(w).exists()).toBe(true)

    // Second poll: lastRanAt advanced → done, fresh counts on screen.
    mockedJobs.mockResolvedValue(
      jobsResponse([
        job('digest-generation', { lastStatus: 'SUCCESS', lastRanAt: '2026-09-16T12:05:00Z', lastResult: { usersProcessed: 1, digestSuccess: 1, digestFail: 0 } }),
        job('email-send'),
        job('cleanup'),
      ])
    )
    await vi.advanceTimersByTimeAsync(3000)
    expect(mockedJobs).toHaveBeenCalledTimes(3)
    expect(runningBadge(w).exists()).toBe(false)
    expect(w.text()).toContain('1 / 1 users · 0 failed')
    expect(runNowButton(w, 0).attributes('disabled')).toBeUndefined()

    // Polling stopped.
    await vi.advanceTimersByTimeAsync(9000)
    expect(mockedJobs).toHaveBeenCalledTimes(3)
    w.unmount()
  })

  it('sends the selected beat as the generation filter', async () => {
    useJobTimers()
    mockedGenerate.mockResolvedValue({ data: { status: 'started' } } as never)
    const w = await mountDashboard()
    await w.get('select').setValue(ALL_TOPICS[0])
    await runNowButton(w, 0).trigger('click')
    await flushPromises()
    expect(mockedGenerate).toHaveBeenCalledWith([ALL_TOPICS[0]])
    w.unmount()
  })

  it('reports an already-running generation with the error toast', async () => {
    useJobTimers()
    mockedGenerate.mockResolvedValue({ data: { status: 'already_running' } } as never)
    const w = await mountDashboard()
    await runNowButton(w, 0).trigger('click')
    await flushPromises()
    expect(toast.error).toHaveBeenCalledWith('Digest generation is already running')
    expect(toast.success).not.toHaveBeenCalled()
    w.unmount()
  })

  it('stops polling after the budget and warns that the job may still be running', async () => {
    useJobTimers()
    mockedGenerate.mockResolvedValue({ data: { status: 'started' } } as never)
    const w = await mountDashboard()
    await runNowButton(w, 0).trigger('click')
    await flushPromises()
    mockedJobs.mockResolvedValue(jobsResponse([job('digest-generation', { running: true }), job('email-send'), job('cleanup')]))

    await vi.advanceTimersByTimeAsync(219 * 3000)
    expect(toast.error).not.toHaveBeenCalled()
    expect(runningBadge(w).exists()).toBe(true)

    await vi.advanceTimersByTimeAsync(3000)
    expect(toast.error).toHaveBeenCalledWith(
      'The job is taking unusually long — it may still be running. Refresh later for final counts.'
    )
    expect(runningBadge(w).exists()).toBe(false)
    const calls = mockedJobs.mock.calls.length
    await vi.advanceTimersByTimeAsync(9000)
    expect(mockedJobs.mock.calls.length).toBe(calls)
    w.unmount()
  })

  it('shows the error state with retry when loading fails, and recovers', async () => {
    mockedStats.mockRejectedValueOnce(new Error('boom'))
    const w = await mountDashboard()
    expect(w.text()).toContain("— Couldn't load —")
    expect(w.text()).toContain("The desk wouldn't load.")
    expect(toast.error).toHaveBeenCalledWith('Failed to load dashboard data')
    expect(w.findAll('article')).toHaveLength(0)

    await w.get('button').trigger('click')
    await flushPromises()
    expect(w.findAll('article')).toHaveLength(3)
    expect(w.text()).toContain('Generate digests')
  })
})
