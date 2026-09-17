import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'

const options = [
  { value: 'en', label: 'English', lang: 'en' },
  { value: 'ko', label: '한국어', lang: 'ko' },
]

describe('SegmentedControl', () => {
  it('renders an accessible radiogroup with one checked cell', () => {
    const w = mount(SegmentedControl, {
      props: { options, modelValue: 'ko', label: 'Edition' },
    })
    const legend = w.get('fieldset legend')
    expect(legend.text()).toBe('Edition')
    expect(legend.classes()).toContain('sr-only')
    expect(legend.attributes('id')).toBeTruthy()

    // The legend is the group's one accessible name.
    const group = w.get('[role="radiogroup"]')
    expect(group.attributes('aria-labelledby')).toBe(legend.attributes('id'))
    expect(group.attributes('aria-label')).toBeUndefined()
    expect(group.attributes('style')).toContain('repeat(2, minmax(0, 1fr))')

    const radios = w.findAll('[role="radio"]')
    expect(radios.map((r) => r.attributes('lang'))).toEqual(['en', 'ko'])
    expect(radios.map((r) => r.attributes('aria-checked'))).toEqual(['false', 'true'])
    expect(radios.map((r) => r.attributes('type'))).toEqual(['button', 'button'])
    expect(radios[0]!.classes()).toContain('border-r')
    expect(radios[1]!.classes()).not.toContain('border-r')
    expect(radios[1]!.classes()).toContain('bg-ink')
  })

  it('emits update:modelValue with the chosen value and omits lang when not given', async () => {
    const w = mount(SegmentedControl, {
      props: {
        options: [{ value: 'light', label: 'Day' }, { value: 'dark', label: 'Night' }, { value: 'system', label: 'System' }],
        modelValue: 'light',
        label: 'Lights',
      },
    })
    const radios = w.findAll('[role="radio"]')
    expect(radios).toHaveLength(3)
    expect(radios[0]!.attributes('lang')).toBeUndefined()
    await radios[2]!.trigger('click')
    expect(w.emitted('update:modelValue')).toEqual([['system']])
  })
})
