import { getCurrentScope, onScopeDispose, ref, readonly } from 'vue'

export interface PollerOptions {
  /** Delay before each tick (the first tick fires after one interval). */
  intervalMs: number
  /**
   * One poll. Resolve `'done'` to stop, `'continue'` to arm the next tick.
   * `isCurrent()` is false once `stop()` was called (or `start()` restarted the
   * loop) while this tick was in flight — check it before applying a response.
   * A rejected tick counts as one failure and the loop keeps going.
   */
  tick: (isCurrent: () => boolean) => Promise<'done' | 'continue'>
  /** Stop after this many ticks and call `onExhausted`. Unbounded when omitted. */
  maxAttempts?: number
  /** Stop after this many consecutive failed ticks and call `onFailed`. Unbounded when omitted. */
  maxConsecutiveFailures?: number
  onExhausted?: () => void
  onFailed?: () => void
}

/**
 * A self-scheduling poll loop. Ticks never overlap: the next one is armed only
 * after the previous response lands (setInterval with an async body would pile
 * requests up behind a slow server). Stops itself when the owning component
 * scope is disposed.
 */
export function usePoller(options: PollerOptions) {
  const active = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let run = 0 // bumps on every start/stop so in-flight ticks can tell they are stale
  let attempts = 0
  let consecutiveFailures = 0

  const clearTimer = () => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  const stop = () => {
    run += 1
    clearTimer()
    active.value = false
  }

  const schedule = (currentRun: number) => {
    timer = setTimeout(async () => {
      timer = null
      if (currentRun !== run) return
      const isCurrent = () => currentRun === run
      attempts += 1
      let outcome: 'done' | 'continue' = 'continue'
      try {
        outcome = await options.tick(isCurrent)
        consecutiveFailures = 0
      } catch {
        consecutiveFailures += 1
      }
      if (!isCurrent()) return
      if (outcome === 'done') {
        stop()
        return
      }
      if (options.maxConsecutiveFailures !== undefined && consecutiveFailures >= options.maxConsecutiveFailures) {
        stop()
        options.onFailed?.()
        return
      }
      if (options.maxAttempts !== undefined && attempts >= options.maxAttempts) {
        stop()
        options.onExhausted?.()
        return
      }
      schedule(currentRun)
    }, options.intervalMs)
  }

  const start = () => {
    stop()
    attempts = 0
    consecutiveFailures = 0
    active.value = true
    schedule(run)
  }

  if (getCurrentScope()) {
    onScopeDispose(stop)
  }

  return { start, stop, active: readonly(active) }
}
