import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ArchiveSearch from '@/components/archive/ArchiveSearch.vue'

const mountSearch = (props: Partial<{ query: string; loading: boolean; active: boolean; matchCount: number }> = {}) =>
  mount(ArchiveSearch, { props: { query: '', loading: false, active: false, matchCount: 0, ...props } })

const status = (w: ReturnType<typeof mountSearch>) => w.get('p[aria-live="polite"]').text()

describe('ArchiveSearch', () => {
  it('binds the query and shows Clear only once something is typed', async () => {
    const w = mountSearch()
    expect(w.get('#archive-search').attributes('placeholder')).toBe('Search headlines and stories…')
    expect(w.find('button').exists()).toBe(false)

    await w.get('#archive-search').setValue('claude')
    expect(w.emitted('update:query')).toEqual([['claude']])

    await w.setProps({ query: 'claude' })
    await w.get('button').trigger('click')
    expect(w.emitted('clear')).toHaveLength(1)
  })

  it('reports searching, then the match count, otherwise the hint', async () => {
    const w = mountSearch()
    expect(status(w)).toBe('Searches your full 30-day archive, beyond the 7-day view below')

    await w.setProps({ query: 'claude', loading: true })
    expect(status(w)).toBe('Searching…')

    await w.setProps({ loading: false, active: true, matchCount: 1 })
    expect(status(w)).toBe('1 story matches · full 30-day archive')

    await w.setProps({ matchCount: 3 })
    expect(status(w)).toBe('3 stories match · full 30-day archive')
  })
})
