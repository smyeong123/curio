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

// The Edition dropdown, named by its screen-reader-only <label>.
const editionSelect = (wrapper: ReturnType<typeof mountSettings>) => wrapper.get('select#settings-edition')

describe('SettingsView editions', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('renders the English edition with numbered sections and the Edition dropdown', async () => {
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

    expect(w.get('label[for="settings-edition"]').text()).toBe('Edition')
    const select = editionSelect(w)
    expect((select.element as HTMLSelectElement).value).toBe('en')
    expect(select.findAll('option').map((o) => [o.attributes('lang'), o.text()])).toEqual([
      ['en', 'English'],
      ['ko', '한국어']
    ])
  })

  it('switches to the Korean edition from the Edition section and persists the choice', async () => {
    const w = mountSettings()
    await flushPromises()

    await editionSelect(w).setValue('ko')
    expect(i18n.global.locale.value).toBe('ko')
    expect(localStorage.setItem).toHaveBeenCalledWith(LOCALE_STORAGE_KEY, 'ko')

    expect(w.get('h1').text()).toBe('내 설정.')
    const text = w.text()
    expect(text).toContain('§ III — 언어')
    expect(text).toContain('§ IV — 비밀번호')
    expect(text).toContain('기기 시간대 자동 적용')
    expect(text).toContain('시스템을 고르면 기기 설정을 따라가요')
    expect(text).toContain('3개 선택')
    expect(text).toContain('프런티어 연구소')
    expect(text).toContain('관심 주제 저장')
    expect(text).not.toContain('Frontier Labs')

    expect(w.get('label[for="settings-edition"]').text()).toBe('언어')
    expect((editionSelect(w).element as HTMLSelectElement).value).toBe('ko')

    // Switching back restores the English copy byte-for-byte.
    await editionSelect(w).setValue('en')
    expect(w.get('h1').text()).toBe('Your settings.')
    expect(w.text()).toContain('§ III — Edition')
  })
})
