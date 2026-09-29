import { describe, it, expect, beforeEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { TOPIC_HIERARCHY, ALL_TOPICS } from '@/data/topics'
import { i18n, messages } from '@/i18n'

const Probe = defineComponent({
  setup() {
    const labels = useTopicLabels()
    return () => h('div', [
      h('span', { id: 'leaf' }, labels.topicLabel('Reasoning & Context')),
      h('span', { id: 'lab' }, labels.topicLabel('Claude (Anthropic)')),
      h('span', { id: 'unknown' }, labels.topicLabel('Not A Topic')),
      h('span', { id: 'l1' }, labels.groupName(TOPIC_HIERARCHY[0]!)),
      h('span', { id: 'l2' }, labels.groupName(TOPIC_HIERARCHY[0]!.subcategories[0]!)),
      h('span', { id: 'desc' }, labels.groupDescription(TOPIC_HIERARCHY[0]!)),
    ])
  }
})

describe('useTopicLabels', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders canonical names untouched in English', () => {
    const w = mount(Probe)
    expect(w.get('#leaf').text()).toBe('Reasoning & Context')
    expect(w.get('#l1').text()).toBe('Frontier Labs')
    expect(w.get('#l2').text()).toBe('Proprietary Frontier')
    expect(w.get('#desc').text()).toBe('New features and releases from the major model labs')
  })

  it('maps to Korean labels, leaving lab names and unknowns alone', async () => {
    const w = mount(Probe)
    i18n.global.locale.value = 'ko'
    await w.vm.$nextTick()
    expect(w.get('#leaf').text()).toBe('추론·컨텍스트')
    expect(w.get('#lab').text()).toBe('Claude (Anthropic)')
    expect(w.get('#unknown').text()).toBe('Not A Topic')
    expect(w.get('#l1').text()).toBe('주요 AI 연구소')
    expect(w.get('#l2').text()).toBe('비공개 대형 모델')
    expect(w.get('#desc').text()).toBe('주요 AI 연구소의 새 모델과 기능 소식')
  })

  it('only maps names that exist in the taxonomy', () => {
    const leaves = (messages.ko.topics as { leaves: Record<string, string> }).leaves
    for (const name of Object.keys(leaves)) {
      expect(ALL_TOPICS).toContain(name)
    }
    const groups = (messages.ko.topics as { groups: Record<string, unknown> }).groups
    const ids = TOPIC_HIERARCHY.flatMap((l1) => [l1.id, ...l1.subcategories.map((l2) => l2.id)])
    for (const id of Object.keys(groups)) {
      expect(ids).toContain(id)
    }
  })
})
