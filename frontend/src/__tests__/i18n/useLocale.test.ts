import { describe, it, expect, beforeEach, vi } from 'vitest'
import { detectLocale, i18n, LOCALE_STORAGE_KEY, messages } from '@/i18n'
import { useLocale, syncDocumentLocale } from '@/composables/useLocale'

describe('i18n catalogs', () => {
  it('ship every namespace in both editions', () => {
    expect(Object.keys(messages.en).sort()).toEqual(Object.keys(messages.ko).sort())
    expect(Object.keys(messages.en)).toContain('common')
  })

  it('ship the same key tree in both editions', () => {
    const flatten = (obj: unknown, prefix = ''): string[] =>
      obj && typeof obj === 'object' && !Array.isArray(obj)
        ? Object.entries(obj as Record<string, unknown>).flatMap(([k, v]) => flatten(v, prefix ? `${prefix}.${k}` : k))
        : [prefix]
    // topics.leaves is intentionally sparse in English (canonical names render as-is)
    const strip = (keys: string[]) => keys.filter((k) => !k.startsWith('topics.leaves.')).sort()
    expect(strip(flatten(messages.ko))).toEqual(strip(flatten(messages.en)))
  })
})

describe('detectLocale', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('prefers a stored explicit choice', () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'ko')
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['en-US'])
    expect(detectLocale()).toBe('ko')
  })

  it('falls back to the browser language', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['ko-KR', 'en-US'])
    expect(detectLocale()).toBe('ko')
  })

  it('defaults to English for unsupported languages', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['ja-JP'])
    expect(detectLocale()).toBe('en')
  })

  it('ignores garbage in storage', () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'xx')
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['en-US'])
    expect(detectLocale()).toBe('en')
  })
})

describe('useLocale', () => {
  beforeEach(() => {
    localStorage.clear()
    i18n.global.locale.value = 'en'
  })

  it('switches the edition, persists it, and stamps <html lang> + title', async () => {
    const { locale, intlLocale, setLocale } = useLocale()
    expect(locale.value).toBe('en')
    expect(intlLocale.value).toBe('en-US')

    setLocale('ko')
    await Promise.resolve()
    expect(locale.value).toBe('ko')
    expect(intlLocale.value).toBe('ko-KR')
    expect(i18n.global.t('common.language.label')).toBe('언어')
    expect(localStorage.setItem).toHaveBeenCalledWith(LOCALE_STORAGE_KEY, 'ko')
    expect(document.documentElement.lang).toBe('ko')
    expect(document.title).toBe('Curio — AI 모델 뉴스 일간지')
  })

  it('toggles between the two editions', async () => {
    const { locale, toggleLocale } = useLocale()
    toggleLocale()
    expect(locale.value).toBe('ko')
    toggleLocale()
    expect(locale.value).toBe('en')
  })

  it('syncDocumentLocale reflects the current edition', () => {
    syncDocumentLocale()
    expect(document.documentElement.lang).toBe('en')
    expect(document.title).toBe('Curio — A daily for AI model news')
  })
})
