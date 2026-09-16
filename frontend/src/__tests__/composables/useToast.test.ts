import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useToast } from '@/composables/useToast'

describe('useToast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // Reset toasts between tests
    const { toasts } = useToast()
    toasts.value = []
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('adds a toast', () => {
    const { addToast, toasts } = useToast()
    addToast('Hello', 'info')
    expect(toasts.value.length).toBe(1)
    expect(toasts.value[0].message).toBe('Hello')
    expect(toasts.value[0].type).toBe('info')
  })

  it('success adds success toast', () => {
    const { success, toasts } = useToast()
    success('Done!')
    expect(toasts.value[toasts.value.length - 1].type).toBe('success')
  })

  it('error adds error toast', () => {
    const { error, toasts } = useToast()
    error('Failed!')
    expect(toasts.value[toasts.value.length - 1].type).toBe('error')
  })

  it('removes toast by id', () => {
    const { addToast, removeToast, toasts } = useToast()
    addToast('Test', 'info')
    const id = toasts.value[toasts.value.length - 1].id
    removeToast(id)
    expect(toasts.value.find(t => t.id === id)).toBeUndefined()
  })

  it('auto-removes after duration', () => {
    const { addToast, toasts } = useToast()
    addToast('Temp', 'info', 3000)
    const id = toasts.value[toasts.value.length - 1].id
    expect(toasts.value.find(t => t.id === id)).toBeDefined()
    vi.advanceTimersByTime(3000)
    expect(toasts.value.find(t => t.id === id)).toBeUndefined()
  })
})
