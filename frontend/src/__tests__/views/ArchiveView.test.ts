import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'
import ArchiveView from '@/views/dashboard/ArchiveView.vue'
import { i18n } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/onboarding', component: { template: '<div/>' } },
    { path: '/dashboard/archive', name: 'archive', component: { template: '<div/>' } },
    { path: '/dashboard/quiz/:digestId', name: 'quiz', component: { template: '<div/>' } },
  ]
})

// One issue from today so it lands inside the 7-day browse window.
const digest = {
  id: 'digest-0a1',
  content: {
    summaries: [
      {
        headline: 'Claude gains longer context windows',
        summary: 'A summary of the story.',
        why_it_matters: 'Because context is king.',
        source_url: 'https://example.com/story',
        source_name: 'Tech Daily',
        topic: 'Reasoning & Context'
      }
    ],
    generatedFor: ['Reasoning & Context']
  },
  generatedAt: new Date().toISOString(),
  emailSentAt: new Date().toISOString()
}

// Actions are stubbed by the testing pinia, so `load()` resolves without a
// network call and the view renders straight from `initialState`.
const mountArchive = async () => {
  const wrapper = mount(ArchiveView, {
    global: {
      plugins: [
        router,
        createTestingPinia({ createSpy: vi.fn, initialState: { news: { digests: [digest] } } })
      ]
    }
  })
  await flushPromises()
  return wrapper
}

describe('ArchiveView editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the English edition by default with canonical topic names', async () => {
    const w = await mountArchive()
    const text = w.text()
    expect(w.get('h1').text()).toBe('Today on Curio.')
    expect(text).toContain('Your edition')
    expect(text).toContain('Issues in archive')
    expect(text).toContain('Day 1 of 1')
    expect(text).toContain('last 7 days')
    expect(text).toContain('Delivered')
    expect(text).toContain('Take quiz')
    expect(text).toContain('Why it matters')
    expect(text).toContain('Source: Tech Daily')
    expect(text).toContain('Reasoning & Context')
    expect(text).toContain('Frontier Labs')
    expect(text).toContain('All beats')
    expect(text).toContain('— End of issue —')
    expect(w.get('#archive-search').attributes('placeholder')).toBe('Search headlines and stories…')
  })

  it('renders the Korean edition with translated chrome, topic labels and dates', async () => {
    i18n.global.locale.value = 'ko'
    const w = await mountArchive()
    const text = w.text()
    expect(w.get('h1').text()).toBe('오늘의 Curio.')
    expect(text).toContain('나의 에디션')
    expect(text).toContain('보관된 호')
    expect(text).toContain('1일 중 1일째')
    expect(text).toContain('최근 7일')
    expect(text).toContain('발송')
    expect(text).toContain('퀴즈 풀기')
    expect(text).toContain('왜 중요한가')
    expect(text).toContain('출처: Tech Daily')
    expect(text).toContain('— 이번 호 끝 —')
    // Topic ids stay canonical in data but render through the edition's labels.
    expect(text).toContain('추론 & 컨텍스트')
    expect(text).not.toContain('Reasoning & Context')
    expect(text).toContain('프런티어 랩')
    expect(text).toContain('모든 토픽')
    // Dates go through Intl with the Korean locale.
    expect(text).toMatch(/\d{4}년/)
    expect(w.get('#archive-search').attributes('placeholder')).toBe('헤드라인과 스토리 검색…')
    // Digest content itself is English data and is left untouched.
    expect(text).toContain('Claude gains longer context windows')
  })
})
