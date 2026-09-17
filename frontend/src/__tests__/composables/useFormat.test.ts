import { describe, it, expect, beforeEach } from 'vitest'
import { useFormat } from '@/composables/useFormat'
import { i18n } from '@/i18n'

const AT = '2026-09-16T08:05:00'

describe('useFormat', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('formats the named date styles in English', () => {
    const { formatDate, formatDateTime, formatTime, formatNumber } = useFormat()
    expect(formatDate(AT, 'full')).toBe('Wednesday, September 16, 2026')
    expect(formatDate(AT, 'short')).toBe('Sep 16, 2026')
    expect(formatDate(AT, 'stamp')).toBe('Sep 16, 2026')
    expect(formatDateTime(AT, 'short')).toBe('Sep 16, 2026, 08:05 AM')
    expect(formatDateTime(AT, 'compact')).toBe('Sep 16, 08:05 AM')
    expect(formatTime(AT)).toBe('08:05 AM')
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatNumber(undefined)).toBe('0')
  })

  it('formats batch durations in seconds under a minute and minutes above', () => {
    const { formatDuration } = useFormat()
    expect(formatDuration(12345)).toBe('12.3 sec')
    expect(formatDuration(250000)).toBe('4.2 min')
    expect(formatDuration(0)).toBe('0 sec')
  })

  it('follows the edition', () => {
    i18n.global.locale.value = 'ko'
    const { formatDate, formatNumber } = useFormat()
    expect(formatDate(AT, 'full')).toBe('2026년 9월 16일 수요일')
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(useFormat().formatDuration(12345)).toBe('12.3초')
  })
})
