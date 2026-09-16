import { ref, watch } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'curio:theme'

const preference = ref<ThemePreference>(loadPreference())

function loadPreference(): ThemePreference {
  if (typeof localStorage === 'undefined') return 'system'
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  return 'system'
}

// Keep these in sync with --paper in src/assets/styles/main.css (light / dark).
const THEME_COLOR = { light: '#f3ede1', dark: '#14130f' } as const

function prefersDark(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches
}

// Point the browser-chrome color (mobile address bar, PWA splash) at the paper
// tone actually being shown. Driven from here — not from static media-query meta
// tags — so it also tracks the app's explicit light/dark toggle, not just the OS.
function applyThemeColor(pref: ThemePreference) {
  if (typeof document === 'undefined') return
  const effective = pref === 'system' ? (prefersDark() ? 'dark' : 'light') : pref
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]:not([media])')
  if (meta) meta.setAttribute('content', THEME_COLOR[effective])
}

function apply(pref: ThemePreference) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  // Keep the `data-theme` attribute and the `.dark` class in sync. main.ts sets
  // both on first paint; if we only touched the attribute here, switching to
  // 'system' would leave a stale `.dark` class behind and `:root.dark` would
  // pin the page to dark regardless of the OS preference.
  if (pref === 'system') {
    root.removeAttribute('data-theme')
    root.classList.remove('dark')
  } else {
    root.setAttribute('data-theme', pref)
    root.classList.toggle('dark', pref === 'dark')
  }
  applyThemeColor(pref)
}

apply(preference.value)

// While on 'system', follow live OS theme flips so the chrome color stays honest.
if (typeof window !== 'undefined' && window.matchMedia) {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (preference.value === 'system') applyThemeColor('system')
  })
}

watch(preference, (value) => {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, value)
  }
  apply(value)
})

export function useTheme() {
  const setTheme = (pref: ThemePreference) => {
    preference.value = pref
  }

  return {
    preference,
    setTheme
  }
}
