import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * Scroll state for a sticky nav bar (`.nav-glass`): `scrolled` once the bar is
 * stuck to the top of the viewport (frosted background + hairline), `hidden`
 * while the reader scrolls DOWN past `hideAfter` px (slides away, returns on
 * the first scroll up), and `progress` 0→1 through the page for the reading
 * bar. One passive listener, batched to a frame.
 *
 * The bar never hides under prefers-reduced-motion, or while focus is inside
 * it (a keyboard user tabbing through the nav must be able to see it).
 */
export function useScrollNav(bar: Ref<HTMLElement | null>, { hideAfter = 160 } = {}) {
  const scrolled = ref(false)
  const hidden = ref(false)
  const progress = ref(0)

  let lastY = 0
  let frame = 0
  let reducedMotion = false

  const update = () => {
    frame = 0
    const y = Math.max(0, window.scrollY)
    const max = document.documentElement.scrollHeight - window.innerHeight
    // Stuck = the bar sits at (or, while hidden, above) the top edge after the
    // page has scrolled. Read live so restored scroll positions are right too.
    const top = bar.value?.getBoundingClientRect().top ?? 0
    scrolled.value = y > 0 && top <= 0
    progress.value = max > 0 ? Math.min(1, y / max) : 0

    const delta = y - lastY
    if (Math.abs(delta) < 6) return // ignore trackpad jitter
    const focusInside = !!bar.value?.contains(document.activeElement)
    hidden.value = !reducedMotion && !focusInside && delta > 0 && y > hideAfter
    lastY = y
  }

  const onScroll = () => {
    if (!frame) frame = requestAnimationFrame(update)
  }
  const onFocusIn = () => {
    hidden.value = false
  }

  onMounted(() => {
    reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
    lastY = window.scrollY
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })
    bar.value?.addEventListener('focusin', onFocusIn)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll)
    window.removeEventListener('resize', onScroll)
    bar.value?.removeEventListener('focusin', onFocusIn)
    if (frame) cancelAnimationFrame(frame)
  })

  return { scrolled, hidden, progress }
}
