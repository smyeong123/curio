import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RegisterForm from '@/components/auth/RegisterForm.vue'

describe('RegisterForm', () => {
  const mountForm = (props = {}) => mount(RegisterForm, {
    props: { loading: false, ...props }
  })

  it('renders all input fields', () => {
    const wrapper = mountForm()
    expect(wrapper.find('#register-name').exists()).toBe(true)
    expect(wrapper.find('#register-email').exists()).toBe(true)
    expect(wrapper.find('#register-password').exists()).toBe(true)
    expect(wrapper.find('#register-confirm').exists()).toBe(true)
  })

  it('shows validation error for empty fields', async () => {
    const wrapper = mountForm()
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Full name is required')
    expect(wrapper.text()).toContain('Email is required')
    expect(wrapper.text()).toContain('Password is required')
  })

  it('shows error for short password', async () => {
    const wrapper = mountForm()
    await wrapper.find('#register-name').setValue('John')
    await wrapper.find('#register-email').setValue('john@example.com')
    await wrapper.find('#register-password').setValue('short')
    await wrapper.find('#register-confirm').setValue('short')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Password must be at least 8 characters')
  })

  it('shows error for mismatched passwords', async () => {
    const wrapper = mountForm()
    await wrapper.find('#register-name').setValue('John')
    await wrapper.find('#register-email').setValue('john@example.com')
    await wrapper.find('#register-password').setValue('password123')
    await wrapper.find('#register-confirm').setValue('different123')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Passwords do not match')
  })

  it('emits submit with valid data', async () => {
    const wrapper = mountForm()
    await wrapper.find('#register-name').setValue('John Doe')
    await wrapper.find('#register-email').setValue('john@example.com')
    await wrapper.find('#register-password').setValue('password123')
    await wrapper.find('#register-confirm').setValue('password123')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]).toEqual([{
      email: 'john@example.com',
      password: 'password123',
      fullName: 'John Doe'
    }])
  })

  it('displays server error', () => {
    const wrapper = mountForm({ serverError: 'Email already exists' })
    expect(wrapper.text()).toContain('Email already exists')
  })
})
