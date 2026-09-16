import { computed, watch } from 'vue'
import { i18n, INTL_LOCALE, LOCALE_STORAGE_KEY, SUPPORTED_LOCALES, type Locale } from '@/i18n'

/**
 * Which edition the UI is set in. Mirrors useTheme: one shared piece of state,
 * persisted only on an EXPLICIT choice — a browser-detected locale is never
 * written to storage, so a user who never picked keeps following their browser.
 * Also keeps <html lang> and the document title honest, which is what screen
 * readers, hyphenation and the `:lang(ko)` font rules in main.css key off.
 */
const current = i18n.global.locale

export function syncDocumentLocale() {
  if (typeof document === 'undefined') return
  document.documentElement.lang = current.value
  document.title = i18n.global.t('common.app.title')
}

watch(current, syncDocumentLocale)

export function useLocale() {
  const locale = computed(() => current.value as Locale)
  const intlLocale = computed(() => INTL_LOCALE[locale.value])

  const setLocale = (next: Locale) => {
    current.value = next
    try {
      localStorage.setItem(LOCALE_STORAGE_KEY, next)
    } catch {
      /* ignore storage errors */
    }
  }

  const toggleLocale = () => setLocale(locale.value === 'en' ? 'ko' : 'en')

  return { locale, intlLocale, locales: SUPPORTED_LOCALES, setLocale, toggleLocale }
}
