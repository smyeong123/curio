import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import LanguageSelect from '@/components/ui/LanguageSelect.vue'
import { i18n } from '@/i18n'

describe('LanguageSelect', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('inline variant shows the current edition and switches from the dropdown', async () => {
    const wrapper = mount(LanguageSelect)
    const select = wrapper.get('select')
    expect(wrapper.get('span[lang]').text()).toBe('English')
    expect(select.attributes('aria-label')).toBe('Language')
    expect((select.element as HTMLSelectElement).value).toBe('en')
    // Each option names its edition in its own language.
    expect(select.findAll('option').map((o) => [o.attributes('lang'), o.text()])).toEqual([
      ['en', 'English'],
      ['ko', '한국어'],
    ])

    await select.setValue('ko')
    expect(i18n.global.locale.value).toBe('ko')
    expect(wrapper.get('span[lang]').text()).toBe('한국어')
    expect(wrapper.get('span[lang]').attributes('lang')).toBe('ko')
    expect(select.attributes('aria-label')).toBe('언어 선택')
    expect((select.element as HTMLSelectElement).value).toBe('ko')
  })

  it('row variant pairs the Edition label with the dropdown', async () => {
    const wrapper = mount(LanguageSelect, { props: { variant: 'row' } })
    expect(wrapper.get('.kicker').text()).toBe('Edition')
    expect(wrapper.get('span[lang]').text()).toBe('English')

    await wrapper.get('select').setValue('ko')
    expect(i18n.global.locale.value).toBe('ko')
    expect(wrapper.get('.kicker').text()).toBe('언어')
    expect(wrapper.get('span[lang]').text()).toBe('한국어')
  })

  it('ignores values that are not a supported edition', async () => {
    const wrapper = mount(LanguageSelect)
    const select = wrapper.get('select').element as HTMLSelectElement
    const option = document.createElement('option')
    option.value = 'xx'
    select.appendChild(option)
    select.value = 'xx'
    await wrapper.get('select').trigger('change')
    expect(i18n.global.locale.value).toBe('en')
  })
})
