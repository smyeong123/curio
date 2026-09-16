import { watch, nextTick, onUnmounted, type Ref } from 'vue'

interface FocusTrapOptions {
  /** Called when Escape is pressed inside the trap (typically closes the dialog). */
  onEscape?: () => void
  /**
   * Whether to restore focus to the element that was focused before the trap
   * activated. Evaluated at deactivation time; return false to skip (e.g. when
   * closing because we're navigating away, so the old trigger no longer exists).
   * Defaults to always restoring.
   */
  restoreFocus?: () => boolean
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Modal-dialog focus management, shared by BaseModal and the DashboardLayout
 * mobile drawer: on open, move focus into the container and lock body scroll;
 * while open, cycle Tab within the container and close on Escape; on close,
 * unlock scroll and restore focus to the trigger.
 *
 * `containerRef` is the dialog element (may be null until the `v-if` renders —
 * we read it inside a `nextTick`). `isOpen` drives activation.
 */
export function useFocusTrap(
  containerRef: Ref<HTMLElement | null>,
  isOpen: Ref<boolean>,
  options: FocusTrapOptions = {}
) {
  let lastFocused: HTMLElement | null = null
  let active = false

  const getFocusable = (): HTMLElement[] => {
    if (!containerRef.value) return []
    return Array.from(containerRef.value.querySelectorAll<HTMLElement>(FOCUSABLE))
  }

  const onKeydown = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault()
      options.onEscape?.()
      return
    }
    if (e.key !== 'Tab') return
    const focusable = getFocusable()
    if (focusable.length === 0) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const el = document.activeElement as HTMLElement | null
    const inside = containerRef.value?.contains(el) ?? false
    if (e.shiftKey) {
      if (el === first || !inside) {
        e.preventDefault()
        last?.focus()
      }
    } else if (el === last || !inside) {
      e.preventDefault()
      first?.focus()
    }
  }

  const activate = () => {
    if (active || typeof window === 'undefined') return
    active = true
    lastFocused = document.activeElement as HTMLElement | null
    window.addEventListener('keydown', onKeydown)
    document.body.style.overflow = 'hidden'
    nextTick(() => getFocusable()[0]?.focus())
  }

  const deactivate = () => {
    if (!active) return
    active = false
    window.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = ''
    const shouldRestore = options.restoreFocus ? options.restoreFocus() : true
    if (shouldRestore) lastFocused?.focus()
    lastFocused = null
  }

  watch(isOpen, (open) => (open ? activate() : deactivate()), { immediate: true })

  onUnmounted(() => {
    if (active) {
      window.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
      active = false
    }
  })

  return { getFocusable }
}
