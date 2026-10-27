import { describe, it, expect } from 'vitest'
import { parseApiDate } from '@/utils/apiDate'

describe('parseApiDate', () => {
  it('reads zone-less API date-times as UTC', () => {
    expect(parseApiDate('2026-10-26T20:00:00').toISOString()).toBe('2026-10-26T20:00:00.000Z')
    expect(parseApiDate('2026-10-26T20:00:00.123456').getTime()).toBe(Date.UTC(2026, 9, 26, 20, 0, 0, 123))
    expect(parseApiDate('2026-10-26T20:00').getTime()).toBe(Date.UTC(2026, 9, 26, 20))
  })

  it('leaves values that carry a zone alone', () => {
    expect(parseApiDate('2026-10-26T20:00:00Z').getTime()).toBe(Date.UTC(2026, 9, 26, 20))
    expect(parseApiDate('2026-10-27T05:00:00+09:00').getTime()).toBe(Date.UTC(2026, 9, 26, 20))
  })

  it('reads a date-only string as that local calendar day', () => {
    const d = parseApiDate('2026-10-26')
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours()]).toEqual([2026, 9, 26, 0])
  })

  it('passes Date objects through', () => {
    const now = new Date()
    expect(parseApiDate(now)).toBe(now)
  })
})
