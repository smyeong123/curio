import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AuthColophon from '@/components/auth/AuthColophon.vue'
import { i18n } from '@/i18n'

describe('AuthColophon', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('prints the colophon, the language menu and an upper-cased date stamp', () => {
    const w = mount(AuthColophon)
    expect(w.text()).toContain('© 2026 — set in Fraunces')
    const [colophon, stamp] = w.findAll('p.kicker')
    expect(colophon!.attributes('style')).toContain('var(--paper)')
    expect(stamp!.text()).toMatch(/^[A-Z]{3} \d{2}, \d{4}$/)
    expect(w.get('span[lang]').text()).toBe('English')
  })

  it('re-renders the date stamp in the chosen edition', async () => {
    const w = mount(AuthColophon)
    await w.get('select').setValue('ko')
    expect(i18n.global.locale.value).toBe('ko')
    expect(w.findAll('p.kicker')[1]!.text()).toMatch(/\d{4}년/)
    expect(w.get('span[lang]').text()).toBe('한국어')
  })
})
