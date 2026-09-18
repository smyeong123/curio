import { describe, it, expect, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import StatsView from '@/views/admin/StatsView.vue'
import { i18n } from '@/i18n'

vi.mock('@/services/api', () => ({
  api: {
    admin: {
      getStats: vi.fn(),
      getTopicDistribution: vi.fn(),
    },
  },
}))

import { api } from '@/services/api'

const mockedStats = vi.mocked(api.admin.getStats)
const mockedDistribution = vi.mocked(api.admin.getTopicDistribution)

const mountStats = async () => {
  const w = mount(StatsView)
  await flushPromises()
  return w
}

describe('StatsView editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
    mockedStats.mockResolvedValue({
      data: { totalUsers: 1234, emailsSentToday: 16, quizCompletionsToday: 9 },
    } as never)
    mockedDistribution.mockResolvedValue({
      data: { 'Claude (Anthropic)': 20, 'Reasoning & Context': 15 },
    } as never)
  })

  it('renders the English edition by default with canonical topic names', async () => {
    const w = await mountStats()
    expect(w.get('h1').text()).toBe('The numbers.')
    expect(w.text()).toContain('Newsroom — Numbers')
    expect(w.text()).toContain('Total readers')
    expect(w.text()).toContain('Editions delivered today')
    expect(w.text()).toContain('— Beat distribution —')
    expect(w.text()).toContain('1,234')
    expect(w.text()).toContain('Frontier Labs')
    expect(w.text()).toContain('Claude (Anthropic)')
    expect(w.text()).toContain('Reasoning & Context')
    expect(w.text()).toContain('20 subscribers')
    expect(w.text()).toContain('15 subscribers')
  })

  it('re-renders in Korean when the edition switches', async () => {
    const w = await mountStats()
    i18n.global.locale.value = 'ko'
    await w.vm.$nextTick()

    expect(w.get('h1').text()).toBe('통계 한눈에.')
    expect(w.get('h1 em').text()).toBe('통계')
    expect(w.text()).toContain('뉴스룸 — 숫자')
    expect(w.text()).toContain('전체 독자')
    expect(w.text()).toContain('오늘 발송한 다이제스트')
    expect(w.text()).toContain('— 주제별 분포 —')
    expect(w.text()).toContain('구독자 20명')
    expect(w.text()).toContain('구독자 15명')
    // Group names and mapped leaves are translated; unmapped leaves stay canonical.
    expect(w.text()).toContain('프런티어 연구소')
    expect(w.text()).toContain('추론 & 컨텍스트')
    expect(w.text()).toContain('Claude (Anthropic)')
    expect(w.text()).not.toContain('Total readers')
  })

  it('shows the error state copy in both editions', async () => {
    mockedStats.mockRejectedValueOnce(new Error('boom'))
    const w = await mountStats()
    expect(w.text()).toContain("— Couldn't load —")
    expect(w.get('button').text()).toBe('Retry')

    i18n.global.locale.value = 'ko'
    await w.vm.$nextTick()
    expect(w.text()).toContain('— 불러오지 못했어요 —')
    expect(w.get('button').text()).toBe('다시 시도')
  })
})
