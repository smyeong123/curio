import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { i18n } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: ['/', '/login', '/register', '/privacy', '/terms', '/contact'].map((path) => ({
    path,
    component: { template: '<div/>' }
  }))
})

const mountHome = () => mount(HomeView, { global: { plugins: [router] } })

describe('HomeView editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the English edition by default', () => {
    const w = mountHome()
    expect(w.get('h1').text()).toBe('The AI beat, curated before coffee.')
    expect(w.text()).toContain('Read tomorrow\'s edition')
    expect(w.text()).toContain('Reasoning & Context')
    expect(w.findAll('.ticker-track > span').length).toBe(12)
  })

  it('re-renders in Korean when the masthead toggle is clicked', async () => {
    const w = mountHome()
    const toggle = w.get('header nav button')
    expect(toggle.text()).toBe('한국어')

    await toggle.trigger('click')
    expect(i18n.global.locale.value).toBe('ko')
    expect(w.get('h1').text()).toBe('커피보다 먼저, 골라 담은 AI 소식.')
    expect(w.get('h1 em').text()).toBe('커피')
    expect(w.text()).toContain('내일 자 에디션 읽기')
    expect(w.text()).toContain('추론 & 컨텍스트')
    expect(w.text()).toContain('Curio 편집부')
    expect(w.findAll('.ticker-track > span')[0]!.text()).toContain('Anthropic, 확장 추론 출시')
    expect(w.findAll('ol li h3')[0]!.text()).toBe('토픽을 고르세요')
    expect(toggle.text()).toBe('English')
  })
})
