import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import StudioView from '@/views/dashboard/StudioView.vue'
import { useToast } from '@/composables/useToast'
import type { StudioStatus, StudioTask, StudioTaskState } from '@/types/studio'

vi.mock('@/services/api', () => ({
  api: {
    studio: { getStatus: vi.fn(), generate: vi.fn(), sendEmail: vi.fn() }
  }
}))

import { api } from '@/services/api'

const POLL_MS = 1500

const task = (type: StudioTask['type'], state: StudioTaskState): StudioTask => ({ type, state })

const status = (
  digest: StudioTaskState = 'IDLE',
  email: StudioTaskState = 'IDLE',
  overview: Partial<StudioStatus['overview']> = {}
): StudioStatus => ({
  digest: task('digest', digest),
  email: task('email', email),
  overview: {
    email: 'reader@curio.test',
    topics: ['Claude (Anthropic)'],
    topicCount: 1,
    latestDigest: { id: 'digest-1', generatedAt: '2026-09-16T08:05:00Z', emailSentAt: null },
    hasUnsentDigest: true,
    ...overview
  }
})

const resolveStatus = (...statuses: StudioStatus[]) => {
  const mock = vi.mocked(api.studio.getStatus)
  for (const s of statuses) mock.mockResolvedValueOnce({ data: s } as any)
  return mock
}

let wrapper: VueWrapper | undefined

const mountStudio = async () => {
  wrapper = mount(StudioView, {
    global: { stubs: { ApiKeyManager: true, RouterLink: RouterLinkStub } }
  })
  await flushPromises()
  return wrapper
}

const generateButton = (w: VueWrapper) =>
  w.findAll('button').find((b) => /Generate today|Working/.test(b.text()))!

describe('StudioView', () => {
  beforeEach(() => {
    // Only timers are faked: flushPromises() schedules on setImmediate, which must stay real.
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    // Reset (not clear) so a once-value left unconsumed by one test never leaks into the next.
    vi.resetAllMocks()
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
  })

  it('renders the overview from the status call and does not poll while idle', async () => {
    resolveStatus(status())
    const w = await mountStudio()

    expect(w.get('h1').text()).toBe('Run your own edition.')
    const text = w.text()
    expect(text).toContain('reader@curio.test')
    expect(text).toContain('1 topics')
    expect(text).toContain('Claude (Anthropic)')
    expect(text).toContain('An unsent digest is ready to send')
    expect(text).toContain('Generated Sep 16')
    expect(text).toContain('Not yet emailed')
    expect(generateButton(w).attributes('disabled')).toBeUndefined()

    await vi.advanceTimersByTimeAsync(POLL_MS * 4)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(1)
  })

  it('disables Generate while the digest task is active', async () => {
    resolveStatus(status('RUNNING'), status('RUNNING'), status('SUCCESS'))
    const w = await mountStudio()

    expect(generateButton(w).text()).toContain('Working…')
    expect(generateButton(w).attributes('disabled')).toBeDefined()

    await vi.advanceTimersByTimeAsync(POLL_MS * 2)
    expect(generateButton(w).text()).toContain("Generate today's digest")
    expect(generateButton(w).attributes('disabled')).toBeUndefined()
  })

  it('disables Generate when the user has no topics', async () => {
    resolveStatus(status('IDLE', 'IDLE', { topics: [], topicCount: 0 }))
    const w = await mountStudio()

    expect(w.text()).toContain("You haven't picked any topics yet.")
    expect(generateButton(w).attributes('disabled')).toBeDefined()
  })

  it('polls while a task is active and stops once both tasks are idle', async () => {
    resolveStatus(status('IDLE', 'RUNNING'), status('IDLE', 'RUNNING'), status('IDLE', 'SUCCESS'))
    await mountStudio()
    expect(api.studio.getStatus).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(3)

    await vi.advanceTimersByTimeAsync(POLL_MS * 10)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(3)
  })

  it('applies the queued task and starts polling after Generate', async () => {
    resolveStatus(status(), status('SUCCESS'))
    vi.mocked(api.studio.generate).mockResolvedValue({ data: task('digest', 'QUEUED') } as any)
    const w = await mountStudio()

    await generateButton(w).trigger('click')
    await flushPromises()
    expect(api.studio.generate).toHaveBeenCalledTimes(1)
    expect(generateButton(w).text()).toContain('Working…')
    expect(w.find('.animate-pulse').exists()).toBe(true) // queued: indeterminate bar

    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(2)
    expect(w.text()).toContain('SUCCESS')
    await vi.advanceTimersByTimeAsync(POLL_MS * 4)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(2)
  })

  it('gives up after five consecutive failed polls and releases the stuck task', async () => {
    resolveStatus(status('RUNNING')).mockRejectedValue(new Error('down'))
    const w = await mountStudio()

    await vi.advanceTimersByTimeAsync(POLL_MS * 4)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(5)
    expect(generateButton(w).attributes('disabled')).toBeDefined()

    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(6)
    expect(w.text()).toContain('Lost connection')
    expect(generateButton(w).attributes('disabled')).toBeUndefined()
    expect(useToast().toasts.value.some((t) => t.message === 'Lost connection to Studio — reload to refresh status')).toBe(true)

    await vi.advanceTimersByTimeAsync(POLL_MS * 4)
    expect(api.studio.getStatus).toHaveBeenCalledTimes(6)
  })
})
