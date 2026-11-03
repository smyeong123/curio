import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'
import VerifyEmailView from '@/views/auth/VerifyEmailView.vue'
import { useAuthStore } from '@/stores/auth'

const makeRouter = () =>
  createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/verify-email', name: 'verify-email', component: VerifyEmailView },
      { path: '/onboarding', name: 'onboarding', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } }
    ]
  })

const mountAt = async (url: string, confirm?: (token: string) => Promise<unknown>) => {
  const router = makeRouter()
  const pinia = createTestingPinia({ createSpy: vi.fn })
  const auth = useAuthStore(pinia)
  if (confirm) vi.mocked(auth.confirmEmail).mockImplementation(confirm as never)
  await router.push(url)
  await router.isReady()
  const w = mount(VerifyEmailView, { global: { plugins: [router, pinia] } })
  await flushPromises()
  return { w, router, auth }
}

describe('VerifyEmailView', () => {
  it('confirms the link, strips the token from the URL and continues to onboarding', async () => {
    const { router, auth } = await mountAt('/verify-email?token=abc', async () => ({}))

    expect(auth.confirmEmail).toHaveBeenCalledWith('abc')
    expect(router.currentRoute.value.name).toBe('onboarding')
    expect(router.currentRoute.value.fullPath).not.toContain('abc')
  })

  it('offers a fresh link when the token is rejected', async () => {
    const { w, router } = await mountAt('/verify-email?token=stale', async () => {
      throw { response: { status: 400, data: { code: 'CONFIRMATION_TOKEN_INVALID' } } }
    })

    expect(router.currentRoute.value.fullPath).toBe('/verify-email')
    expect(w.get('h1').text()).toBe('That link has expired.')
    expect(w.find('#verify-email-address').exists()).toBe(true)
    expect(w.get('[data-test="resend-confirmation"]').text()).toBe('Resend link')
  })

  it('goes straight to the resend form when there is no token at all', async () => {
    const { w, auth } = await mountAt('/verify-email')

    expect(auth.confirmEmail).not.toHaveBeenCalled()
    expect(w.get('h1').text()).toBe('That link has expired.')
  })
})
