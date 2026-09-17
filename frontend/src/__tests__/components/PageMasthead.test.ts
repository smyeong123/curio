import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PageMasthead from '@/components/ui/PageMasthead.vue'

describe('PageMasthead', () => {
  it('renders the kicker, an h1 with the emphasised slot and the intro slot', () => {
    const w = mount(PageMasthead, {
      props: { kicker: 'Section VII — Colophon', keypath: 'settings.header.headline', emphasis: 'settings', emphasisText: 'settings' },
      slots: { default: '<p class="intro">Intro copy</p>' }
    })
    expect(w.get('p.kicker').text()).toBe('Section VII — Colophon')
    expect(w.get('h1').text()).toBe('Your settings.')
    expect(w.get('h1 em').text()).toBe('settings')
    expect(w.get('.intro').text()).toBe('Intro copy')
    expect(w.find('.rule-double').exists()).toBe(true)

    // Without an aside the headline block still sits in the spaced wrapper, alone.
    const wrapper = w.get('header > div')
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['flex', 'items-end', 'justify-between', 'mb-6']))
    expect(wrapper.element.children).toHaveLength(1)
    expect(w.get('h1').classes()).toEqual(expect.arrayContaining(['display-headline', 'text-[clamp(48px,7vw,96px)]']))
  })

  it('uses the compact headline size for detail pages', () => {
    const w = mount(PageMasthead, {
      props: { kicker: 'Reader', keypath: 'settings.header.headline', emphasis: 'settings', emphasisText: 'settings', size: 'compact' }
    })
    expect(w.get('h1').classes()).toContain('text-[clamp(36px,5vw,64px)]')
    expect(w.get('h1').classes()).not.toContain('text-[clamp(48px,7vw,96px)]')
  })

  it('places an aside beside the headline when the aside slot is used', () => {
    const w = mount(PageMasthead, {
      props: { kicker: 'My edition', keypath: 'archive.header.headline', emphasis: 'brand' },
      slots: { emphasis: 'Curio', aside: '<div class="aside">01</div>' }
    })
    expect(w.get('h1').text()).toBe('Today on Curio.')
    expect(w.get('.aside').text()).toBe('01')

    const wrapper = w.get('header > div')
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['flex', 'items-end', 'justify-between', 'mb-6']))
    expect(wrapper.element.children).toHaveLength(2)
    expect(wrapper.element.lastElementChild).toBe(w.get('.aside').element)
  })
})
