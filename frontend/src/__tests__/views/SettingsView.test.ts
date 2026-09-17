import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'
import SettingsView from '@/views/dashboard/SettingsView.vue'
import { i18n, LOCALE_STORAGE_KEY } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'home', component: { template: '<div/>' } }]
})

// Canonical topic names, exactly as the backend stores them.
const SAVED_TOPICS = ['Claude (Anthropic)', 'DeepSeek', 'Reasoning & Context']

const mountSettings = () =>
  mount(SettingsView, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            user: {
              profile: { email: 'reader@example.com', fullName: 'Reader', hasPassword: true, deliveryEnabled: true },
              preferences: SAVED_TOPICS
            }
          }
        }),
        router
      ],
      // The BYOK manager calls the API on mount and carries its own copy; out of scope here.
      stubs: { ApiKeyManager: true }
    }
  })

// A SegmentedControl is named by its screen-reader legend (aria-labelledby).
const radioGroup = (wrapper: ReturnType<typeof mountSettings>, label: string) => {
  const legend = wrapper.findAll('legend').find((l) => l.text() === label)!
  return wrapper.findAll('[role="radiogroup"]').find((g) => g.attributes('aria-labelledby') === legend.attributes('id'))!
}
const editionRadios = (wrapper: ReturnType<typeof mountSettings>, label: string) =>
  radioGroup(wrapper, label).findAll('[role="radio"]')

describe('SettingsView editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the English edition with numbered sections and the Edition selector', async () => {
    const w = mountSettings()
    await flushPromises()

    expect(w.get('h1').text()).toBe('Your settings.')
    const text = w.text()
    expect(text).toContain('§ I — Profile')
    expect(text).toContain('§ II — Lights')
    expect(text).toContain('§ III — Edition')
    expect(text).toContain('§ IV — Password')
    expect(text).toContain('§ VI — Beats')
    expect(text).toContain('§ VII — Delete account')
    expect(text).toContain("Automatically use my device's timezone")
    expect(text).toContain('System follows your device preference')
    expect(text).toContain('3 selected')
    expect(text).toContain('Frontier Labs')
    expect(text).toContain('Update preferences')

    const radios = editionRadios(w, 'Edition')
    expect(radios.map((r) => r.attributes('lang'))).toEqual(['en', 'ko'])
    expect(radios.map((r) => r.attributes('aria-checked'))).toEqual(['true', 'false'])
    expect(radios[0]!.text()).toBe('English')
    expect(radios[1]!.text()).toBe('한국어')
  })

  it('switches to the Korean edition from the Edition section and persists the choice', async () => {
    const w = mountSettings()
    await flushPromises()

    await w.get('[role="radio"][lang="ko"]').trigger('click')
    expect(i18n.global.locale.value).toBe('ko')
    expect(localStorage.setItem).toHaveBeenCalledWith(LOCALE_STORAGE_KEY, 'ko')

    expect(w.get('h1').text()).toBe('나의 설정.')
    const text = w.text()
    expect(text).toContain('§ III — 언어')
    expect(text).toContain('§ IV — 비밀번호')
    expect(text).toContain('내 기기의 시간대를 자동으로 사용')
    expect(text).toContain('시스템은 기기 설정을 따라요')
    expect(text).toContain('3개 선택')
    expect(text).toContain('프런티어 랩')
    expect(text).toContain('토픽 저장')
    expect(text).not.toContain('Frontier Labs')

    const radios = editionRadios(w, '언어')
    expect(radios.map((r) => r.attributes('aria-checked'))).toEqual(['false', 'true'])

    // Switching back restores the English copy byte-for-byte.
    await w.get('[role="radio"][lang="en"]').trigger('click')
    expect(w.get('h1').text()).toBe('Your settings.')
    expect(w.text()).toContain('§ III — Edition')
  })
})
