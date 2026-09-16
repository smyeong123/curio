import { useUserStore } from '@/stores/user'
import { detectBrowserTimezone } from '@/utils/timezone'

/**
 * Keeps a user's delivery timezone tracking their device so the digest lands at
 * their local delivery hour even when they travel. Runs once when an authenticated
 * user enters the app:
 *
 *  - If the timezone is in AUTO mode (default) and the device's zone differs from
 *    the stored one, it updates the stored zone to the device's — so a Seoul→NY
 *    traveler starts getting 08:00 NY automatically.
 *  - If the user has PINNED a zone in Settings (timezoneAuto = false), it does
 *    nothing, respecting their explicit choice.
 *
 * The digest send-gate defaults an unset timezone to UTC, so without this a user
 * would get 08:00 UTC (e.g. 17:00 in Seoul) rather than 08:00 local.
 */
export function useEnsureTimezone() {
  const userStore = useUserStore()

  const ensure = async () => {
    try {
      // Need the persisted values to know the stored timezone and auto/pinned mode.
      if (userStore.preferences.length === 0) {
        await userStore.fetchPreferences()
      }
      // Pinned → never auto-change. Also needs a valid topic list (the preferences
      // endpoint requires 3+ topics).
      if (userStore.timezoneAuto === false || userStore.preferences.length < 3) {
        return
      }
      const device = detectBrowserTimezone()
      if (device !== userStore.timezone) {
        await userStore.updatePreferences(userStore.preferences, {
          timezone: device,
          deliveryHour: userStore.deliveryHour ?? 8,
          timezoneAuto: true,
        })
      }
    } catch {
      // Non-fatal: best-effort convenience; on failure the existing timezone stays.
    }
  }

  return { ensure }
}
