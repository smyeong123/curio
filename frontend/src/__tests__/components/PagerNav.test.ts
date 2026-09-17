import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PagerNav from '@/components/ui/PagerNav.vue'

const labels = { newerLabel: 'Newer', olderLabel: 'Older' }

describe('PagerNav', () => {
  it('renders the labels and the page counter', () => {
    const w = mount(PagerNav, { props: { page: 1, totalPages: 3, ...labels } })
    expect(w.text()).toContain('Newer')
    expect(w.text()).toContain('Older')
    expect(w.text()).toContain('Page 2 of 3')
    expect(w.findAll('.num-tab').map((s) => s.text())).toEqual(['2', '3'])
  })

  it('disables Newer on the first page and Older on the last', () => {
    const first = mount(PagerNav, { props: { page: 0, totalPages: 2, ...labels } })
    const [newer, older] = first.findAll('button')
    expect(newer!.attributes('disabled')).toBeDefined()
    expect(older!.attributes('disabled')).toBeUndefined()

    const last = mount(PagerNav, { props: { page: 1, totalPages: 2, ...labels } })
    const [newer2, older2] = last.findAll('button')
    expect(newer2!.attributes('disabled')).toBeUndefined()
    expect(older2!.attributes('disabled')).toBeDefined()
  })

  it('emits change with the neighbouring page', async () => {
    const w = mount(PagerNav, { props: { page: 1, totalPages: 3, ...labels } })
    const [newer, older] = w.findAll('button')
    await newer!.trigger('click')
    await older!.trigger('click')
    expect(w.emitted('change')).toEqual([[0], [2]])
  })

  it('renders the counter from a caller-supplied catalog message', () => {
    const w = mount(PagerNav, { props: { page: 1, totalPages: 3, counterKeypath: 'archive.paging.dayOf', ...labels } })
    expect(w.text()).toContain('Day 2 of 3')
    expect(w.text()).not.toContain('Page 2 of 3')
  })

  it('passes extra classes through to the nav', () => {
    const w = mount(PagerNav, { props: { page: 0, totalPages: 2, ...labels }, attrs: { class: 'mt-2' } })
    expect(w.get('nav').classes()).toEqual(expect.arrayContaining(['flex', 'mt-2']))
  })
})
