import { describe, it, expect } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import BeatFilter from '@/components/archive/BeatFilter.vue'
import type { TopicGroup } from '@/components/archive/BeatFilter.vue'
import { i18n } from '@/i18n'

const groups: TopicGroup[] = [
  {
    id: 'frontier-labs',
    name: 'Frontier Labs',
    icon: 'brain',
    topics: [
      { name: 'Claude (Anthropic)', count: 2 },
      { name: 'Mistral', count: 0 }
    ],
    activeCount: 1
  }
]

const mountFilter = (activeTopic = '') =>
  mount(BeatFilter, { props: { groups, activeTopic, totalActive: 1 } })

// Chips live inside the panel; the collapsed header repeats the active label.
const chip = (w: VueWrapper, text: string) =>
  w.get('#beat-filter-panel').findAll('button').find((b) => b.text().startsWith(text))!

describe('BeatFilter', () => {
  it('starts collapsed and discloses the panel, then each big topic', async () => {
    const w = mountFilter()
    const toggle = w.get('button[aria-controls="beat-filter-panel"]')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.text()).toContain('Filter')
    expect(toggle.text()).toContain('All beats')
    expect(toggle.text()).toContain('1 beats')
    expect(w.get('p.sr-only').text()).toBe('No topic filter')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')

    const group = w.get('button[aria-controls="filter-group-frontier-labs"]')
    expect(group.attributes('aria-expanded')).toBe('false')
    expect(group.text()).toContain('Frontier Labs')
    expect(group.text()).toContain('1 active')
    await group.trigger('click')
    expect(group.attributes('aria-expanded')).toBe('true')
    expect(w.find('#filter-group-frontier-labs').exists()).toBe(true)
  })

  it('emits select for a beat and for "All beats"', async () => {
    const w = mountFilter()
    await chip(w, 'Claude (Anthropic)').trigger('click')
    expect(w.emitted('select')).toEqual([['Claude (Anthropic)']])

    await chip(w, 'All beats').trigger('click')
    expect(w.emitted('select')).toEqual([['Claude (Anthropic)'], ['']])
  })

  it('shows the active beat and toggles it off on a second click', async () => {
    const w = mountFilter('Claude (Anthropic)')
    expect(w.get('button[aria-controls="beat-filter-panel"]').text()).toContain('Claude (Anthropic)')
    expect(w.get('p.sr-only').text()).toBe('Filter: Claude (Anthropic)')

    const active = chip(w, 'Claude (Anthropic)')
    expect(active.attributes('aria-pressed')).toBe('true')
    expect(chip(w, 'All beats').attributes('aria-pressed')).toBe('false')

    await active.trigger('click')
    expect(w.emitted('select')).toEqual([['']])
  })

  it('disables beats with no stories so they cannot be selected', async () => {
    const w = mountFilter()
    const mistral = chip(w, 'Mistral')
    expect(mistral.attributes('disabled')).toBeDefined()
    expect(mistral.attributes('aria-pressed')).toBeUndefined()
    expect(mistral.text()).toContain('0')

    await mistral.trigger('click')
    expect(w.emitted('select')).toBeUndefined()
  })

  it('renders its chrome in the Korean edition', () => {
    i18n.global.locale.value = 'ko'
    const w = mountFilter()
    expect(w.get('button[aria-controls="beat-filter-panel"]').text()).toContain('전체')
    expect(w.get('p.sr-only').text()).toBe('전체 주제 보기')
  })
})
