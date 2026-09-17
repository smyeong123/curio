import { describe, it, expect } from 'vitest'
import { useDisclosureSet } from '@/composables/useDisclosureSet'

describe('useDisclosureSet', () => {
  it('starts with nothing open', () => {
    const { openKeys, isOpen } = useDisclosureSet()
    expect(openKeys.value.size).toBe(0)
    expect(isOpen('a')).toBe(false)
  })

  it('toggles keys independently of each other', () => {
    const { openKeys, isOpen, toggle } = useDisclosureSet()
    toggle('a')
    toggle('b')
    expect(isOpen('a')).toBe(true)
    expect(isOpen('b')).toBe(true)
    expect([...openKeys.value]).toEqual(['a', 'b'])

    toggle('a')
    expect(isOpen('a')).toBe(false)
    expect(isOpen('b')).toBe(true)
    expect(openKeys.value.has('a')).toBe(false)
  })
})
