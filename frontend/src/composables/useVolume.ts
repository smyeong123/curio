import { computed } from 'vue'

// Volume = the daily edition number, counting from the 1 Aug 2026 production
// launch (Vol. 001 on launch day, +1 each day after). Clamped to ≥1 so the
// pre-launch masthead never shows 000 or a negative number. Shared by every
// masthead (home, auth pages, dashboard) so they never disagree.
export const VOLUME_EPOCH = Date.UTC(2026, 7, 1) // 1 Aug 2026, 00:00 UTC (month is 0-indexed)

export function volumeAt(now: number): number {
  return Math.max(1, Math.floor((now - VOLUME_EPOCH) / 86_400_000) + 1)
}

const pad = (n: number) => String(n).padStart(3, '0')

export function useVolume() {
  const volume = computed(() => volumeAt(Date.now()))
  return {
    volumeLabel: computed(() => pad(volume.value)),
    // The home sample card previews tomorrow's edition.
    nextVolumeLabel: computed(() => pad(volume.value + 1)),
  }
}
