import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'
import RegisterView from '@/views/auth/RegisterView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: ['/', '/login', '/register', '/onboarding'].map((path) => ({
    path,
    component: { template: '<div/>' }
  }))
})

describe('RegisterView', () => {
  it('shows the check-your-inbox panel after sign-up instead of signing in', async () => {
    const pinia = createTestingPinia({ createSpy: vi.fn })
    const auth = useAuthStore(pinia)
    vi.mocked(auth.register).mockResolvedValue({ status: 'confirmation_sent' })
    await router.push('/register')
    const w = mount(RegisterView, { global: { plugins: [router, pinia] } })

    await w.get('#register-name').setValue('Jane Reader')
    await w.get('#register-email').setValue('jane@example.com')
    await w.get('#register-password').setValue('Password123!')
    await w.get('#register-confirm').setValue('Password123!')
    await w.get('form').trigger('submit')
    await flushPromises()

    expect(auth.register).toHaveBeenCalled()
    expect(w.text()).toContain('Check your inbox.')
    expect(w.text()).toContain('We sent a confirmation link to jane@example.com.')
    expect(w.find('form').exists()).toBe(false)
    // The link was just sent, so resend starts on its cooldown.
    expect(w.get('[data-test="resend-confirmation"]').attributes('disabled')).toBeDefined()
    expect(router.currentRoute.value.path).toBe('/register')
  })
})
