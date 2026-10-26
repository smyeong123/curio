import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScrollWords from '@/components/ui/ScrollWords.vue'

describe('ScrollWords', () => {
  it('gives screen readers the sentence once and splits the visual copy into words', () => {
    const w = mount(ScrollWords, { props: { text: 'Three steps,  every morning.' } })
    expect(w.element.tagName).toBe('P')
    expect(w.get('.sr-only').text()).toBe('Three steps,  every morning.')
    const words = w.findAll('.scroll-word')
    expect(words.map((s) => s.text())).toEqual(['Three', 'steps,', 'every', 'morning.'])
    expect(words[3]!.attributes('style')).toContain('--sw-i: 3')
    expect(w.get('[aria-hidden="true"]').text()).toBe('Three steps, every morning.')
  })

  it('renders as another tag when asked', () => {
    const w = mount(ScrollWords, { props: { text: 'Hi', tag: 'blockquote' } })
    expect(w.element.tagName).toBe('BLOCKQUOTE')
  })
})
