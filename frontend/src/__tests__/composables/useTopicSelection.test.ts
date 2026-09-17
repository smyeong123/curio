import { describe, it, expect } from 'vitest'
import { useTopicSelection } from '@/composables/useTopicSelection'
import { TOPIC_HIERARCHY } from '@/data/topics'

const frontier = TOPIC_HIERARCHY[0]!
const proprietary = frontier.subcategories[0]!

describe('useTopicSelection', () => {
  it('starts from a copy of the initial list', () => {
    const initial = ['Claude (Anthropic)']
    const { selectedTopics } = useTopicSelection(initial)
    selectedTopics.value.push('DeepSeek')
    expect(initial).toEqual(['Claude (Anthropic)'])
    expect(selectedTopics.value).toEqual(['Claude (Anthropic)', 'DeepSeek'])
  })

  it('toggles topics in and out of the selection', () => {
    const { selectedTopics, toggleTopic } = useTopicSelection()
    toggleTopic('Claude (Anthropic)')
    toggleTopic('Mistral')
    expect(selectedTopics.value).toEqual(['Claude (Anthropic)', 'Mistral'])
    toggleTopic('Claude (Anthropic)')
    expect(selectedTopics.value).toEqual(['Mistral'])
  })

  it('counts selected topics per domain and subcategory', () => {
    const { toggleTopic, domainCount, subcategoryCount } = useTopicSelection(['Reasoning & Context'])
    expect(domainCount(frontier)).toBe(0)
    toggleTopic('Claude (Anthropic)')
    toggleTopic('DeepSeek')
    expect(subcategoryCount(proprietary)).toBe(1)
    expect(domainCount(frontier)).toBe(2)
  })

  it('unfolds and folds domains', () => {
    const { expandedDomains, toggleDomain } = useTopicSelection()
    expect(expandedDomains.value.has(frontier.id)).toBe(false)
    toggleDomain(frontier.id)
    expect(expandedDomains.value.has(frontier.id)).toBe(true)
    toggleDomain(frontier.id)
    expect(expandedDomains.value.has(frontier.id)).toBe(false)
  })
})
