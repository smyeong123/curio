import { useLocale } from '@/composables/useLocale'

/**
 * Locale-aware date/time/number formatting for the current edition. One place
 * for the handful of styles the UI uses, so views don't declare their own
 * `toLocale*String` helpers with slightly different option sets.
 *
 * Styles are named by role, not by their options, so a view reads as
 * `formatDate(x, 'full')` rather than a five-key options literal. Cypress
 * asserts on the English output of several styles, so each option set is
 * part of the contract.
 */
export type DateStyle = 'full' | 'short' | 'stamp'
export type DateTimeStyle = 'short' | 'compact'

const DATE: Record<DateStyle, Intl.DateTimeFormatOptions> = {
  /** "Wednesday, September 16, 2026" — issue mastheads, quiz history rows. */
  full: { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' },
  /** "Sep 16, 2026" — admin tables. */
  short: { year: 'numeric', month: 'short', day: 'numeric' },
  /** "SEP 16, 2026" once upper-cased — the auth pages' date stamp. */
  stamp: { month: 'short', day: '2-digit', year: 'numeric' },
}

const DATE_TIME: Record<DateTimeStyle, Intl.DateTimeFormatOptions> = {
  /** "Sep 16, 2026, 08:05 AM" — audit log, admin digests, job timestamps. */
  short: { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' },
  /** "Sep 16, 08:05 AM" — studio + dashboard where the year is implied. */
  compact: { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' },
}

export function useFormat() {
  const { intlLocale } = useLocale()

  const formatDate = (value: string | Date, style: DateStyle = 'short') =>
    new Date(value).toLocaleDateString(intlLocale.value, DATE[style])

  const formatDateTime = (value: string | Date, style: DateTimeStyle = 'short') =>
    new Date(value).toLocaleString(intlLocale.value, DATE_TIME[style])

  const formatTime = (value: string | Date) =>
    new Date(value).toLocaleTimeString(intlLocale.value, { hour: '2-digit', minute: '2-digit' })

  const formatNumber = (value?: number | null) => (value ?? 0).toLocaleString(intlLocale.value)

  /** "12.3 sec" / "4.2 min" — how long a batch run took. */
  const formatDuration = (ms: number) => {
    const seconds = ms / 1000
    const inMinutes = seconds >= 60
    return new Intl.NumberFormat(intlLocale.value, {
      style: 'unit',
      unit: inMinutes ? 'minute' : 'second',
      unitDisplay: 'short',
      maximumFractionDigits: 1,
    }).format(inMinutes ? seconds / 60 : seconds)
  }

  return { formatDate, formatDateTime, formatTime, formatNumber, formatDuration }
}
