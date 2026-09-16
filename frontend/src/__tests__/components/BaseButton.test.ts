import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseButton from '@/components/ui/BaseButton.vue'

describe('BaseButton', () => {
  it('renders slot content', () => {
    const wrapper = mount(BaseButton, { slots: { default: 'Click me' } })
    expect(wrapper.text()).toContain('Click me')
  })

  it('defaults to type=button', () => {
    const wrapper = mount(BaseButton)
    expect(wrapper.find('button').attributes('type')).toBe('button')
  })

  it('respects type prop', () => {
    const wrapper = mount(BaseButton, { props: { type: 'submit' } })
    expect(wrapper.find('button').attributes('type')).toBe('submit')
  })

  it('is disabled when disabled prop is true', () => {
    const wrapper = mount(BaseButton, { props: { disabled: true } })
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('is disabled when loading', () => {
    const wrapper = mount(BaseButton, { props: { loading: true } })
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('shows spinner svg when loading', () => {
    const wrapper = mount(BaseButton, { props: { loading: true } })
    expect(wrapper.find('svg.animate-spin').exists()).toBe(true)
  })

  it('does not show spinner when not loading', () => {
    const wrapper = mount(BaseButton, { props: { loading: false } })
    expect(wrapper.find('svg.animate-spin').exists()).toBe(false)
  })

  it('applies size classes for sm', () => {
    const wrapper = mount(BaseButton, { props: { size: 'sm' } })
    expect(wrapper.find('button').classes()).toContain('text-xs')
  })

  it('applies size classes for lg', () => {
    const wrapper = mount(BaseButton, { props: { size: 'lg' } })
    expect(wrapper.find('button').classes()).toContain('py-3')
  })

  it('applies variant classes for danger', () => {
    const wrapper = mount(BaseButton, { props: { variant: 'danger' } })
    expect(wrapper.find('button').classes()).toContain('bg-red-600')
  })

  it('emits click event', async () => {
    const wrapper = mount(BaseButton)
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
  })
})
