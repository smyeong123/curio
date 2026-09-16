import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginForm from '@/components/auth/LoginForm.vue'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: { template: '<div/>' } }, { path: '/reset-password', component: { template: '<div/>' } }]
})

const mountForm = (props = {}) => mount(LoginForm, {
  props: { loading: false, ...props },
  global: { plugins: [router] }
})

describe('LoginForm', () => {
  it('renders email and password inputs', () => {
    const wrapper = mountForm()
    expect(wrapper.find('#login-email').exists()).toBe(true)
    expect(wrapper.find('#login-password').exists()).toBe(true)
  })

  it('shows validation errors when submitting empty form', async () => {
    const wrapper = mountForm()
    await wrapper.find('form').trigger('submit')
    expect(wrapper.text()).toContain('Email is required')
    expect(wrapper.text()).toContain('Password is required')
    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  it('emits submit with email and password', async () => {
    const wrapper = mountForm()
    await wrapper.find('#login-email').setValue('test@example.com')
    await wrapper.find('#login-password').setValue('password123')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('submit')?.[0]).toEqual(['test@example.com', 'password123'])
  })

  it('displays server error', () => {
    const wrapper = mountForm({ serverError: 'Invalid credentials' })
    expect(wrapper.text()).toContain('Invalid credentials')
  })

  it('emits google event when google button clicked', async () => {
    const wrapper = mountForm()
    const googleBtn = wrapper.findAll('button').find(b => b.text().includes('Google'))
    await googleBtn!.trigger('click')
    expect(wrapper.emitted('google')).toBeTruthy()
  })
})
