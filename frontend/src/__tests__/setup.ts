import { beforeEach, vi } from 'vitest'
import { config } from '@vue/test-utils'
import { i18n } from '@/i18n'

// Mock localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value }),
    removeItem: vi.fn((key: string) => { delete store[key] }),
    clear: vi.fn(() => { store = {} })
  }
})()

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock import.meta.env
vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'test-google-client-id')

// Mount every component with the app's own i18n singleton (the one useLocale
// mutates) and reset it to the English edition before each test so copy
// assertions never depend on the machine's browser language or a prior test.
config.global.plugins = [i18n]
beforeEach(() => {
  i18n.global.locale.value = 'en'
})

// jsdom has no matchMedia; views consult it for reduced-motion / dark scheme.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn()
  }))
})
