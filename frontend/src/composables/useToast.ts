import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

const MAX_TOASTS = 5

const toasts = ref<Toast[]>([])
// Per-toast dismiss timers so we can reset them on a re-fire and clear them when a
// toast is removed early or dropped by the MAX_TOASTS cap (no dangling timeouts).
const timers = new Map<number, ReturnType<typeof setTimeout>>()
let nextId = 0

export function useToast() {
  const clearTimer = (id: number) => {
    const handle = timers.get(id)
    if (handle !== undefined) {
      clearTimeout(handle)
      timers.delete(id)
    }
  }

  const removeToast = (id: number) => {
    clearTimer(id)
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  const armTimer = (id: number, duration: number) => {
    clearTimer(id)
    timers.set(id, setTimeout(() => removeToast(id), duration))
  }

  const addToast = (message: string, type: Toast['type'] = 'info', duration = 4000) => {
    // Re-firing an identical toast resets its dismiss timer rather than stacking a
    // duplicate, so a repeated message stays visible for its full duration.
    const existing = toasts.value.find(t => t.message === message && t.type === type)
    if (existing) {
      armTimer(existing.id, duration)
      return existing.id
    }

    const id = nextId++
    toasts.value.push({ id, message, type })

    if (toasts.value.length > MAX_TOASTS) {
      const dropped = toasts.value.slice(0, toasts.value.length - MAX_TOASTS)
      dropped.forEach(t => clearTimer(t.id))
      toasts.value = toasts.value.slice(-MAX_TOASTS)
    }

    armTimer(id, duration)
    return id
  }

  const success = (message: string) => addToast(message, 'success')
  const error = (message: string) => addToast(message, 'error')
  const info = (message: string) => addToast(message, 'info')

  return {
    toasts,
    addToast,
    removeToast,
    success,
    error,
    info
  }
}
