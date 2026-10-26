import { describe, it, expect, afterEach, vi } from 'vitest'
import { useVolume, volumeAt } from '@/composables/useVolume'

describe('useVolume', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('counts editions from the 1 Aug 2026 launch, never below 1', () => {
    expect(volumeAt(Date.UTC(2026, 7, 1, 12))).toBe(1)
    expect(volumeAt(Date.UTC(2026, 7, 2))).toBe(2)
    expect(volumeAt(Date.UTC(2026, 0, 1))).toBe(1)
  })

  it('pads the label to three digits and previews tomorrow', () => {
    vi.useFakeTimers()
    vi.setSystemTime(Date.UTC(2026, 9, 26, 9))
    const { volumeLabel, nextVolumeLabel } = useVolume()
    expect(volumeLabel.value).toBe('087')
    expect(nextVolumeLabel.value).toBe('088')
  })
})
