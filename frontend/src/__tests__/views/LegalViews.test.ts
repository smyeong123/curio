import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, type Component } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import PrivacyView from '@/views/legal/PrivacyView.vue'
import TermsView from '@/views/legal/TermsView.vue'
import ContactView from '@/views/legal/ContactView.vue'
import { i18n } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: ['/', '/login', '/register', '/privacy', '/terms', '/contact'].map((path) => ({
    path,
    component: { template: '<div/>' }
  }))
})

const mountView = (view: Component) => mount(view, { global: { plugins: [router] } })

const EMAIL = 'sangmyeonglee123@gmail.com'
const MAILTO = `.legal-body a[href="mailto:${EMAIL}"]`
const NOTICE = '이 문서는 영문 원본을 번역한 것이에요. 내용이 다르게 해석되면 영문 원본을 따라요.'

describe('Legal views editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('Privacy renders the English edition by default', () => {
    const w = mountView(PrivacyView)
    expect(w.get('h1').text()).toBe('Privacy Policy')
    expect(w.text()).toContain('← Back to home')
    expect(w.text()).toContain('Last updated May 2026')
    expect(w.findAll('.legal-body h2').map((h) => h.text())).toEqual([
      'What we collect',
      'How we use it',
      'Service providers',
      'Cookies & sessions',
      'How long we keep it',
      'Your choices',
      'Changes'
    ])
    // Intro comes first, before any heading (keeps the h2:first-child rule inert as before).
    expect(w.get('.legal-body').element.firstElementChild?.tagName).toBe('P')

    const firstBullet = w.findAll('.legal-body li')[0]!
    expect(firstBullet.get('strong').text()).toBe('Account details')
    expect(firstBullet.text()).toBe(
      'Account details — your email address, and either a password (stored only as a salted hash, never in plain text) or your Google account identifier if you sign in with Google.'
    )
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/contact"]').text()).toBe('Contact us')
    expect(w.text()).not.toContain(NOTICE)
    expect(w.findAll('footer a').map((a) => a.text())).toEqual(['Privacy', 'Terms', 'Contact', EMAIL])
    expect(w.text()).toContain('© 2026 Curio')
  })

  it('Privacy re-renders in Korean when picked from the masthead dropdown', async () => {
    const w = mountView(PrivacyView)
    const current = () => w.get('header span[lang]').text()
    expect(current()).toBe('English')

    await w.get('header select').setValue('ko')
    expect(i18n.global.locale.value).toBe('ko')
    expect(w.get('h1').text()).toBe('개인정보 처리방침')
    expect(w.text()).toContain('— 매일 아침 AI 모델 소식')
    expect(w.text()).toContain('← 홈으로')
    expect(w.text()).toContain('최종 수정일 2026년 5월')
    expect(w.findAll('.legal-body h2').length).toBe(7)
    expect(w.findAll('.legal-body h2')[0]!.text()).toBe('모으는 정보')
    expect(w.findAll('.legal-body li')[0]!.get('strong').text()).toBe('계정 정보')
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/contact"]').text()).toBe('문의하기')
    expect(w.text()).toContain(NOTICE)
    expect(w.findAll('footer a').map((a) => a.text())).toEqual(['개인정보 처리방침', '이용약관', '문의하기', EMAIL])
    expect(current()).toBe('한국어')
  })

  it('Terms renders both editions with the same section structure', async () => {
    const w = mountView(TermsView)
    expect(w.get('h1').text()).toBe('Terms of Service')
    expect(w.text()).toContain('Last updated July 2026')
    const enHeadings = w.findAll('.legal-body h2').map((h) => h.text())
    expect(enHeadings).toEqual([
      'The service',
      'Your account',
      'Acceptable use',
      'AI-generated content',
      'Pricing',
      'Intellectual property',
      'Termination',
      'Disclaimer & liability',
      'Changes & contact'
    ])
    expect(w.findAll('.legal-body strong').map((s) => s.text())).toEqual([
      'may contain errors or omissions',
      'free testing period through 31 July 2026',
      'August–September 2026'
    ])
    expect(w.text()).toContain('The service is provided "as is" without warranties of any kind.')
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/contact"]').text()).toBe('contact page')
    expect(w.text()).not.toContain(NOTICE)

    i18n.global.locale.value = 'ko'
    await nextTick()
    expect(w.get('h1').text()).toBe('이용약관')
    expect(w.text()).toContain('최종 수정일 2026년 7월')
    expect(w.findAll('.legal-body h2').length).toBe(enHeadings.length)
    expect(w.findAll('.legal-body h2')[2]!.text()).toBe('이렇게는 이용할 수 없어요')
    expect(w.findAll('.legal-body strong')[0]!.text()).toBe('틀리거나 빠진 내용이 있을 수 있어요')
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/contact"]').text()).toBe('문의하기')
    expect(w.text()).toContain(NOTICE)
  })

  it('Contact renders both editions without a date or translation notice', async () => {
    const w = mountView(ContactView)
    expect(w.get('h1').text()).toBe('Get in touch')
    expect(w.text()).not.toContain('Last updated')
    expect(w.get('.legal-body p strong').text()).toBe('Sangmyeong Lee')
    expect(w.findAll('.legal-body h2').map((h) => h.text())).toEqual([
      'Email',
      'Pricing & upcoming paid plans',
      'Before you write',
      'New here?'
    ])
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/terms"]').text()).toBe('terms')
    expect(w.get('.legal-body a[href="/register"]').text()).toBe('Subscribe free')
    expect(w.get('.legal-body a[href="/login"]').text()).toBe('sign in')
    expect(w.findAll('.legal-body li')[1]!.text()).toBe(
      'Want to stop emails? Use the unsubscribe link at the bottom of any digest, or turn delivery off in your settings — no need to email us.'
    )

    await w.get('header select').setValue('ko')
    expect(w.get('h1').text()).toBe('문의하기')
    expect(w.text()).not.toContain('최종 수정')
    expect(w.get('.legal-body p strong').text()).toBe('이상명(Sangmyeong Lee)')
    expect(w.findAll('.legal-body h2').length).toBe(4)
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/terms"]').text()).toBe('이용약관')
    expect(w.get('.legal-body a[href="/register"]').text()).toBe('무료로 구독')
    expect(w.get('.legal-body a[href="/login"]').text()).toBe('로그인')
    expect(w.text()).not.toContain(NOTICE)
  })
})
