import { createI18n } from 'vue-i18n'

/**
 * UI editions. Two locales: English (default) and Korean.
 *
 * Message catalogs live in `locales/<locale>/<namespace>.json` and are read as
 * `t('<namespace>.<key>')`. Namespaces are auto-discovered (import.meta.glob),
 * so adding one means adding the two JSON files — nothing to register here.
 * Ship every key in BOTH files; a Korean key that is missing falls back to the
 * English string per key (fallbackLocale), which is a safety net, not a plan.
 *
 * Message-format gotchas (vue-i18n): `{name}` interpolates, `|` separates
 * plural forms, `@` starts a linked message. A literal `@` or `|` must be
 * written as `{'@'}` / `{'|'}` (e-mail addresses!). Inline markup (an <em> in a
 * headline) goes through the `<i18n-t>` component with named slots.
 *
 * Outside components (stores, composables, main.ts) use `i18n.global.t`.
 */
export const SUPPORTED_LOCALES = ['en', 'ko'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: Locale = 'en'
export const LOCALE_STORAGE_KEY = 'curio:locale'

/** BCP-47 tags to hand to Intl / toLocale*String for each edition. */
export const INTL_LOCALE: Record<Locale, string> = { en: 'en-US', ko: 'ko-KR' }

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

/** Explicit choice (localStorage) → browser language → English. */
export function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
    if (isLocale(stored)) return stored
  } catch {
    /* storage unavailable (private mode etc.) */
  }
  if (typeof navigator !== 'undefined') {
    const candidates = navigator.languages?.length ? navigator.languages : [navigator.language]
    for (const tag of candidates) {
      const base = tag?.toLowerCase().split('-')[0]
      if (isLocale(base)) return base
    }
  }
  return DEFAULT_LOCALE
}

// Message types are deliberately SHALLOW (namespace → key → string) even though
// the JSON nests deeper: vue-i18n derives the key type of `t()` from this shape,
// and both its own recursive LocaleMessage type and a faithful nested type make
// that instantiation "excessively deep". Two levels give `t('ns.key…')` a
// `.` key type, which every call in the app satisfies.
type Namespace = Record<string, string>
type Catalog = Record<string, Namespace>

const catalogModules = import.meta.glob<{ default: Namespace }>('./locales/*/*.json', { eager: true })

function buildMessages(): Record<Locale, Catalog> {
  const built: Record<Locale, Catalog> = { en: {}, ko: {} }
  for (const [path, mod] of Object.entries(catalogModules)) {
    const match = /\/locales\/([^/]+)\/([^/]+)\.json$/.exec(path)
    if (!match) continue
    const locale = match[1]
    const namespace = match[2]
    if (!isLocale(locale) || !namespace) continue
    built[locale][namespace] = mod.default
  }
  return built
}

export const messages = buildMessages()

export function createAppI18n(locale: Locale = detectLocale()) {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: DEFAULT_LOCALE,
    messages,
    missingWarn: import.meta.env.DEV,
    fallbackWarn: false,
  })
}

/** The app-wide instance. Components use `useI18n()`; everything else `i18n.global.t`. */
export const i18n = createAppI18n()
