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
const NOTICE = '이 문서는 영문 원본의 번역본이며, 해석에 차이가 있을 경우 영문본이 우선합니다.'

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

  it('Privacy re-renders in Korean when the masthead toggle is clicked', async () => {
    const w = mountView(PrivacyView)
    const toggle = w.get('header button')
    expect(toggle.text()).toBe('한국어')

    await toggle.trigger('click')
    expect(i18n.global.locale.value).toBe('ko')
    expect(w.get('h1').text()).toBe('개인정보 처리방침')
    expect(w.text()).toContain('— AI 모델 뉴스 일간지')
    expect(w.text()).toContain('← 홈으로')
    expect(w.text()).toContain('2026년 5월 최종 수정')
    expect(w.findAll('.legal-body h2').length).toBe(7)
    expect(w.findAll('.legal-body h2')[0]!.text()).toBe('수집하는 정보')
    expect(w.findAll('.legal-body li')[0]!.get('strong').text()).toBe('계정 정보')
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/contact"]').text()).toBe('문의하기')
    expect(w.text()).toContain(NOTICE)
    expect(w.findAll('footer a').map((a) => a.text())).toEqual(['개인정보 처리방침', '이용약관', '문의', EMAIL])
    expect(toggle.text()).toBe('English')
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
    expect(w.text()).toContain('2026년 7월 최종 수정')
    expect(w.findAll('.legal-body h2').length).toBe(enHeadings.length)
    expect(w.findAll('.legal-body h2')[2]!.text()).toBe('허용되는 이용')
    expect(w.findAll('.legal-body strong')[0]!.text()).toBe('오류나 누락이 있을 수 있으며')
    expect(w.get(MAILTO).text()).toBe(EMAIL)
    expect(w.get('.legal-body a[href="/contact"]').text()).toBe('문의 페이지')
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

    await w.get('header button').trigger('click')
    expect(w.get('h1').text()).toBe('연락하기')
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
