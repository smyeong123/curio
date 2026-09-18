import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'
import LoginView from '@/views/auth/LoginView.vue'
import { i18n } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: ['/', '/login', '/register', '/reset-password'].map((path) => ({
    path,
    component: { template: '<div/>' }
  }))
})

const mountLogin = () =>
  mount(LoginView, { global: { plugins: [router, createTestingPinia()] } })

describe('LoginView editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the English edition by default', () => {
    const w = mountLogin()
    expect(w.get('h1').text()).toBe('Welcome back.')
    expect(w.get('h2').text()).toBe('The press is running for you.')
    expect(w.text()).toContain('Continue your daily briefing.')
    expect(w.text()).toContain('Reset it →')
    expect(w.text()).toContain('Continue with Google')
    expect(w.text()).toContain('© 2026 — set in Fraunces')
    expect(w.get('#login-email').attributes('placeholder')).toBe('you@example.com')
    expect(w.text()).toContain('Vol. 047')
  })

  it('re-renders in Korean when picked from the masthead dropdown', async () => {
    const w = mountLogin()
    expect(w.get('section span[lang]').text()).toBe('English')

    await w.get('section select').setValue('ko')
    expect(i18n.global.locale.value).toBe('ko')
    expect(w.get('h1').text()).toBe('다시 만나서 반가워요.')
    expect(w.get('h1 em').text()).toBe('만나서')
    expect(w.get('h2').text()).toBe('오늘도 윤전기가 돌아가고 있어요.')
    expect(w.text()).toContain('비밀번호를 잊으셨나요?')
    expect(w.text()).toContain('재설정하기 →')
    expect(w.text()).toContain('Google로 계속하기')
    expect(w.text()).toContain('아직 계정이 없으신가요?')
    expect(w.get('#login-email').attributes('placeholder')).toBe('you@example.com')
    expect(w.text()).toContain('Vol. 047')
    expect(w.get('section span[lang]').text()).toBe('한국어')

    // Validation copy follows the edition too.
    await w.get('form').trigger('submit')
    expect(w.text()).toContain('이메일을 입력해 주세요.')
    expect(w.text()).toContain('비밀번호를 입력해 주세요.')
  })
})
