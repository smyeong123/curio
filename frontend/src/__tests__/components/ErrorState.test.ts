import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorState from '@/components/ui/ErrorState.vue'
import { i18n } from '@/i18n'

describe('ErrorState', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the kicker-only variant with the shared retry label and emits retry', async () => {
    const w = mount(ErrorState, { props: { kicker: '— Could not load —' } })
    expect(w.find('h3').exists()).toBe(false)
    expect(w.text()).toContain('— Could not load —')
    expect(w.get('button').text()).toBe('Retry')
    await w.get('button').trigger('click')
    expect(w.emitted('retry')).toHaveLength(1)
  })

  it('renders headline and body when given, and a caller-supplied retry label', () => {
    const w = mount(ErrorState, {
      props: { kicker: '— Press jam —', headline: "The morning press didn't run.", body: 'Try again.', retryLabel: 'Reload' }
    })
    expect(w.get('h3').text()).toBe("The morning press didn't run.")
    expect(w.text()).toContain('Try again.')
    expect(w.get('button').text()).toBe('Reload')
    expect(w.get('section').attributes('role')).toBe('alert')
  })

  it('follows the edition for the default retry label', () => {
    i18n.global.locale.value = 'ko'
    const w = mount(ErrorState, { props: { kicker: '— 불러오지 못함 —' } })
    expect(w.get('button').text()).toBe('다시 시도')
  })
})
