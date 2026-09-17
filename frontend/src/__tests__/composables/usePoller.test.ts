import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { usePoller } from '@/composables/usePoller'

describe('usePoller', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('ticks until the tick reports done and never overlaps', async () => {
    let inFlight = 0
    let maxInFlight = 0
    const tick = vi.fn(async () => {
      inFlight += 1
      maxInFlight = Math.max(maxInFlight, inFlight)
      await new Promise((r) => setTimeout(r, 50))
      inFlight -= 1
      return tick.mock.calls.length >= 3 ? ('done' as const) : ('continue' as const)
    })
    const poller = usePoller({ intervalMs: 100, tick })
    poller.start()
    expect(poller.active.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)
    expect(tick).toHaveBeenCalledTimes(3)
    expect(maxInFlight).toBe(1)
    expect(poller.active.value).toBe(false)
  })

  it('stops after the attempt budget and reports exhaustion', async () => {
    const onExhausted = vi.fn()
    const poller = usePoller({ intervalMs: 10, maxAttempts: 4, tick: async () => 'continue', onExhausted })
    poller.start()
    await vi.advanceTimersByTimeAsync(200)
    expect(onExhausted).toHaveBeenCalledTimes(1)
    expect(poller.active.value).toBe(false)
  })

  it('tolerates failures below the budget, gives up at it, and resets on success', async () => {
    const onFailed = vi.fn()
    let calls = 0
    const poller = usePoller({
      intervalMs: 10,
      maxConsecutiveFailures: 2,
      tick: async () => {
        calls += 1
        if (calls === 1 || calls >= 3) throw new Error('blip')
        return 'continue'
      },
      onFailed
    })
    poller.start()
    await vi.advanceTimersByTimeAsync(200)
    // fail, ok (resets), fail, fail → stop
    expect(calls).toBe(4)
    expect(onFailed).toHaveBeenCalledTimes(1)
  })

  it('marks an in-flight tick stale when stopped or restarted', async () => {
    const seen: boolean[] = []
    const poller = usePoller({
      intervalMs: 10,
      tick: async (isCurrent) => {
        await new Promise((r) => setTimeout(r, 30))
        seen.push(isCurrent())
        return 'continue'
      }
    })
    poller.start()
    await vi.advanceTimersByTimeAsync(15) // first tick in flight
    poller.stop()
    await vi.advanceTimersByTimeAsync(100)
    expect(seen).toEqual([false])
    expect(poller.active.value).toBe(false)
  })
})
