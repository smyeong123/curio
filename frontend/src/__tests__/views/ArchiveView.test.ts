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

// Yesterday's issue lands on its own page (one day per page).
const yesterdayDigest = {
  ...digest,
  id: 'digest-0a2',
  content: {
    ...digest.content,
    summaries: [{ ...digest.content.summaries[0]!, headline: 'Yesterday on the model beat' }]
  },
  generatedAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
}

// Actions are stubbed by the testing pinia, so `load()` resolves without a
// network call and the view renders straight from `initialState`.
const mountArchive = async (digests = [digest]) => {
  const wrapper = mount(ArchiveView, {
    global: {
      plugins: [
        router,
        createTestingPinia({ createSpy: vi.fn, initialState: { news: { digests } } })
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

  it('pages one day at a time and scrolls to the top on a page change', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
    const w = await mountArchive([digest, yesterdayDigest])
    expect(w.text()).toContain('Day 1 of 2')
    expect(w.text()).toContain('Claude gains longer context windows')
    expect(w.text()).not.toContain('Yesterday on the model beat')

    const nav = w.get('nav')
    const [newer, older] = nav.findAll('button')
    expect(newer!.text()).toContain('Newer')
    expect(newer!.attributes('disabled')).toBeDefined()
    expect(older!.text()).toContain('Older')

    await older!.trigger('click')
    expect(w.text()).toContain('Day 2 of 2')
    expect(w.text()).toContain('Yesterday on the model beat')
    expect(w.text()).not.toContain('Claude gains longer context windows')
    expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })
    scrollTo.mockRestore()
  })

  it('renders the Korean edition with translated chrome, topic labels and dates', async () => {
    i18n.global.locale.value = 'ko'
    const w = await mountArchive()
    const text = w.text()
    expect(w.get('h1').text()).toBe('오늘의 Curio.')
    expect(text).toContain('내 다이제스트')
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
    expect(text).toContain('프런티어 연구소')
    expect(text).toContain('전체 주제')
    // Dates go through Intl with the Korean locale.
    expect(text).toMatch(/\d{4}년/)
    expect(w.get('#archive-search').attributes('placeholder')).toBe('제목이나 본문 검색…')
    // Digest content itself is English data and is left untouched.
    expect(text).toContain('Claude gains longer context windows')
  })
})
