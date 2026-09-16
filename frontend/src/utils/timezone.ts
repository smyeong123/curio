/**
 * The browser's IANA timezone (e.g. "Asia/Seoul") — the user's actual location,
 * used to schedule their daily digest at their local delivery hour. Falls back to
 * UTC if the runtime can't resolve it.
 */
export function detectBrowserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
