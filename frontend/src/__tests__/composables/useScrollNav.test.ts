import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useScrollNav } from '@/composables/useScrollNav'

// Mount the composable inside a component so its lifecycle hooks run, with a
// real <header> to watch. requestAnimationFrame runs synchronously here.
const mountNav = () => {
  let state!: ReturnType<typeof useScrollNav>
  const Host = defineComponent({
    setup() {
      const bar = ref<HTMLElement | null>(null)
      state = useScrollNav(bar, { hideAfter: 100 })
      return () => h('header', { ref: bar }, [h('a', { href: '#' }, 'link')])
    },
  })
  const wrapper = mount(Host, { attachTo: document.body })
  return { wrapper, state }
}

const scrollTo = (y: number) => {
  Object.defineProperty(window, 'scrollY', { value: y, configurable: true })
  window.dispatchEvent(new Event('scroll'))
}

describe('useScrollNav', () => {
  beforeEach(() => {
    // Runs the frame synchronously and returns 0, so the composable's
    // "frame pending" guard is clear again by the next scroll event.
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => { cb(0); return 0 })
    Object.defineProperty(document.documentElement, 'scrollHeight', { value: 2000, configurable: true })
    Object.defineProperty(window, 'innerHeight', { value: 1000, configurable: true })
    scrollTo(0)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({ matches: false, media: query } as MediaQueryList))
  })

  it('marks the bar scrolled once it is stuck, and tracks reading progress', () => {
    const { state, wrapper } = mountNav()
    expect(state.scrolled.value).toBe(false)
    scrollTo(500)
    expect(state.scrolled.value).toBe(true)
    expect(state.progress.value).toBe(0.5)
    wrapper.unmount()
  })

  it('hides while scrolling down past the threshold and returns on scroll up', () => {
    const { state, wrapper } = mountNav()
    scrollTo(50)
    expect(state.hidden.value).toBe(false) // still above hideAfter
    scrollTo(400)
    expect(state.hidden.value).toBe(true)
    scrollTo(300)
    expect(state.hidden.value).toBe(false)
    wrapper.unmount()
  })

  it('stays put while focus is inside the bar', async () => {
    const { state, wrapper } = mountNav()
    ;(wrapper.find('a').element as HTMLElement).focus()
    scrollTo(400)
    expect(state.hidden.value).toBe(false)
    wrapper.unmount()
  })

  it('never hides under prefers-reduced-motion', () => {
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({ matches: query.includes('reduce'), media: query } as MediaQueryList))
    const { state, wrapper } = mountNav()
    scrollTo(400)
    expect(state.hidden.value).toBe(false)
    expect(state.scrolled.value).toBe(true)
    wrapper.unmount()
  })
})
