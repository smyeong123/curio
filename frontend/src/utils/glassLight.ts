/**
 * Liquid Glass pointer response. One delegated listener for the whole app:
 * while the pointer is over a glass button it writes
 *  - `--gx`/`--gy`, the pointer's position inside the button, which the
 *    `::after` glint follows, and
 *  - `--lx`/`--ly`, a small drift toward the pointer, which the button's
 *    spring transform follows (main.css),
 * and clears the drift when the pointer leaves so the button springs home.
 * Mouse only — touch has no hover — and skipped entirely under reduced motion.
 */
const GLASS_BUTTON = '.btn-editorial, .btn-editorial-ghost, .btn-glass, .btn-glass-clear'
const DRIFT_X = 4 // px at the button's left/right edge
const DRIFT_Y = 3

export function installGlassLight(root: Document = document): () => void {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return () => {}

  let current: HTMLElement | null = null

  const release = (button: HTMLElement) => {
    button.style.removeProperty('--lx')
    button.style.removeProperty('--ly')
  }

  const onMove = (event: PointerEvent) => {
    if (event.pointerType !== 'mouse') return
    const button = (event.target as Element | null)?.closest?.<HTMLElement>(GLASS_BUTTON) ?? null
    if (current && current !== button) release(current)
    current = button
    if (!button || button.matches(':disabled')) return
    const rect = button.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const x = (event.clientX - rect.left) / rect.width
    const y = (event.clientY - rect.top) / rect.height
    button.style.setProperty('--gx', `${(x * 100).toFixed(1)}%`)
    button.style.setProperty('--gy', `${(y * 100).toFixed(1)}%`)
    button.style.setProperty('--lx', `${((x - 0.5) * 2 * DRIFT_X).toFixed(2)}px`)
    button.style.setProperty('--ly', `${((y - 0.5) * 2 * DRIFT_Y).toFixed(2)}px`)
  }

  const onOut = (event: PointerEvent) => {
    if (!current) return
    const next = event.relatedTarget as Node | null
    if (next && current.contains(next)) return
    release(current)
    current = null
  }

  root.addEventListener('pointermove', onMove, { passive: true })
  root.addEventListener('pointerout', onOut, { passive: true })
  return () => {
    root.removeEventListener('pointermove', onMove)
    root.removeEventListener('pointerout', onOut)
    if (current) release(current)
  }
}
