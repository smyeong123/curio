/**
 * Parse a date the API sent. The backend serializes `LocalDateTime` (stored as
 * UTC wall-clock time) with no zone — "2026-10-26T20:00:00" — which
 * `new Date()` would read as LOCAL time, shifting every timestamp by the
 * reader's UTC offset. Zone-less date-times are therefore read as UTC.
 * Date-only strings ("2026-10-26") name a calendar day, so they become local
 * midnight (`new Date()` would make them UTC midnight — the previous day for
 * anyone west of UTC). Anything carrying a zone, and Date objects, pass through.
 */
const ZONELESS_DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

export function parseApiDate(value: string | Date): Date {
  if (value instanceof Date) return value
  if (ZONELESS_DATE_TIME.test(value)) return new Date(`${value}Z`)
  const day = DATE_ONLY.exec(value)
  if (day) return new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3]))
  return new Date(value)
}
