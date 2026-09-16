import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'
import { i18n } from '@/i18n'

describe('LanguageToggle', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('link variant names the other edition and switches on click', async () => {
    const wrapper = mount(LanguageToggle)
    const button = wrapper.get('button')
    expect(button.text()).toBe('한국어')
    expect(button.attributes('lang')).toBe('ko')
    expect(button.attributes('aria-label')).toBe('Switch to 한국어')

    await button.trigger('click')
    expect(i18n.global.locale.value).toBe('ko')
    expect(button.text()).toBe('English')
    expect(button.attributes('lang')).toBe('en')
    expect(button.attributes('aria-label')).toBe('English로 전환')
  })

  it('switch variant shows both editions with the active one highlighted', async () => {
    const wrapper = mount(LanguageToggle, { props: { variant: 'switch' } })
    expect(wrapper.text()).toContain('Edition')
    const [en, ko] = wrapper.findAll('span[lang]')
    expect(en.classes()).toContain('text-[color:var(--ink)]')
    expect(ko.classes()).toContain('text-[color:var(--mute)]')

    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).toContain('언어')
    expect(ko.classes()).toContain('text-[color:var(--ink)]')
  })
})
