import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseInput from '@/components/ui/BaseInput.vue'

describe('BaseInput', () => {
  it('renders label when provided', () => {
    const wrapper = mount(BaseInput, { props: { label: 'Email', modelValue: '' } })
    expect(wrapper.find('label').text()).toBe('Email')
  })

  it('does not render label when not provided', () => {
    const wrapper = mount(BaseInput, { props: { modelValue: '' } })
    expect(wrapper.find('label').exists()).toBe(false)
  })

  it('renders input with correct type', () => {
    const wrapper = mount(BaseInput, { props: { type: 'password', modelValue: '' } })
    expect(wrapper.find('input').attributes('type')).toBe('password')
  })

  it('renders placeholder', () => {
    const wrapper = mount(BaseInput, { props: { placeholder: 'Enter email', modelValue: '' } })
    expect(wrapper.find('input').attributes('placeholder')).toBe('Enter email')
  })

  it('displays error message', () => {
    const wrapper = mount(BaseInput, { props: { error: 'Required field', modelValue: '' } })
    expect(wrapper.text()).toContain('Required field')
  })

  it('does not display error when not provided', () => {
    const wrapper = mount(BaseInput, { props: { modelValue: '' } })
    expect(wrapper.find('p').exists()).toBe(false)
  })

  it('emits update:modelValue on input', async () => {
    const wrapper = mount(BaseInput, { props: { modelValue: '' } })
    await wrapper.find('input').setValue('hello')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['hello'])
  })

  it('sets disabled attribute', () => {
    const wrapper = mount(BaseInput, { props: { disabled: true, modelValue: '' } })
    expect(wrapper.find('input').attributes('disabled')).toBeDefined()
  })

  it('applies error styling to input', () => {
    const wrapper = mount(BaseInput, { props: { error: 'Error', modelValue: '' } })
    expect(wrapper.find('input').classes()).toContain('border-red-300')
  })
})
