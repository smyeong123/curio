import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import AuthMasthead from '@/components/auth/AuthMasthead.vue'
import MailSentPanel from '@/components/auth/MailSentPanel.vue'
import { i18n } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: ['/', '/login'].map((path) => ({ path, component: { template: '<div/>' } })),
})

describe('AuthMasthead', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('pairs the brand with the edition toggle on paper', async () => {
    const w = mount(AuthMasthead, { global: { plugins: [router] }, attrs: { class: 'lg:hidden' } })
    expect(w.get('a').attributes('href')).toBe('/')
    expect(w.text()).toContain('Curio')
    expect(w.text()).toContain('Vol. 047')
    expect(w.element.classList.contains('lg:hidden')).toBe(true)

    const toggle = w.get('button[lang="ko"]')
    expect(toggle.text()).toBe('한국어')
    await toggle.trigger('click')
    expect(i18n.global.locale.value).toBe('ko')
    expect(w.text()).toContain('Vol. 047')
  })

  it('renders the brand alone on the ink aside', () => {
    const w = mount(AuthMasthead, { props: { onInk: true }, global: { plugins: [router] } })
    expect(w.element.tagName).toBe('A')
    expect(w.find('button').exists()).toBe(false)
    expect(w.text()).toContain('Vol. 047')
    expect(w.get('.kicker').attributes('style')).toContain('var(--paper)')
  })
})

describe('MailSentPanel', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('confirms the address the mail went to and links back to sign-in', () => {
    const w = mount(MailSentPanel, {
      props: { email: 'reader@example.com', bodyKeypath: 'auth.reset.sent.body' },
      global: { plugins: [router] },
    })
    expect(w.text()).toContain("— Mail's away —")
    expect(w.text()).toContain('Check your inbox.')
    expect(w.get('strong').text()).toBe('reader@example.com')
    expect(w.get('a').attributes('href')).toBe('/login')
    expect(w.get('a').text()).toBe('← Back to sign in')
  })
})
