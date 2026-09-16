/**
 * Accessibility helper for route transitions.
 *
 * After a page finishes entering, move focus to its main heading so keyboard
 * and screen-reader users land at the top of the new page (and hear its title)
 * instead of being stranded wherever focus was on the previous page.
 *
 * Wire it into a <transition>'s @after-enter hook:
 *   <transition name="page" mode="out-in" @after-enter="focusPageHeading">
 *
 * The heading is focused via a temporary tabindex="-1" (removed on blur) so it
 * never joins the tab order, and `.no-focus-ring` (see main.css) suppresses the
 * visible outline for this programmatic focus while leaving real keyboard focus
 * rings intact everywhere else.
 */
export function focusPageHeading(el: Element): void {
  const root = el as HTMLElement

  // Don't steal focus if the user has already started interacting with the new
  // page (e.g. clicked into a form field before the enter transition finished).
  const active = document.activeElement
  if (
    active instanceof HTMLElement &&
    (active.matches('input, textarea, select, [contenteditable="true"]') ||
      active.isContentEditable)
  ) {
    return
  }

  // Prefer an explicit page heading, fall back to the first h1, then the root.
  const target =
    root.querySelector<HTMLElement>('[data-page-heading]') ??
    root.querySelector<HTMLElement>('h1') ??
    root

  if (!target) return

  const hadTabindex = target.hasAttribute('tabindex')
  if (!hadTabindex) target.setAttribute('tabindex', '-1')
  target.classList.add('no-focus-ring')

  target.focus({ preventScroll: true })

  target.addEventListener(
    'blur',
    () => {
      if (!hadTabindex) target.removeAttribute('tabindex')
      target.classList.remove('no-focus-ring')
    },
    { once: true }
  )
}
