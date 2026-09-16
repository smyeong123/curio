import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import QuizResults from '@/components/quiz/QuizResults.vue'
import { i18n } from '@/i18n'

describe('QuizResults', () => {
  it('displays score', () => {
    const wrapper = mount(QuizResults, { props: { score: 4, total: 5 } })
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('5')
  })

  it('displays percentage', () => {
    const wrapper = mount(QuizResults, { props: { score: 4, total: 5 } })
    expect(wrapper.text()).toContain('80%')
  })

  it('shows excellent message for >= 80%', () => {
    const wrapper = mount(QuizResults, { props: { score: 4, total: 5 } })
    expect(wrapper.text()).toContain('Excellent work!')
  })

  it('shows good job for >= 60%', () => {
    const wrapper = mount(QuizResults, { props: { score: 3, total: 5 } })
    expect(wrapper.text()).toContain('Good job!')
  })

  it('shows keep reading for < 60%', () => {
    const wrapper = mount(QuizResults, { props: { score: 2, total: 5 } })
    expect(wrapper.text()).toContain('Keep reading!')
  })

  it('flags an excellent tier for high scores', () => {
    const wrapper = mount(QuizResults, { props: { score: 5, total: 5 } })
    // Tier is exposed as data-tier so styling can change without breaking the contract.
    expect(wrapper.get('[data-tier]').attributes('data-tier')).toBe('excellent')
  })

  it('emits review when review button clicked', async () => {
    const wrapper = mount(QuizResults, { props: { score: 3, total: 5 } })
    await wrapper.findAll('button')[0].trigger('click')
    expect(wrapper.emitted('review')).toBeTruthy()
  })

  it('emits back when back button clicked', async () => {
    const wrapper = mount(QuizResults, { props: { score: 3, total: 5 } })
    await wrapper.findAll('button')[1].trigger('click')
    expect(wrapper.emitted('back')).toBeTruthy()
  })

  it('renders the Korean edition when the locale is ko', () => {
    i18n.global.locale.value = 'ko'
    const wrapper = mount(QuizResults, { props: { score: 4, total: 5 } })
    expect(wrapper.text()).toContain('— 채점 결과 도착 —')
    expect(wrapper.text()).toContain('아주 훌륭해요!')
    expect(wrapper.text()).toContain('정답률 80%')
    expect(wrapper.text()).toContain('답 확인하기')
    expect(wrapper.text()).toContain('오늘의 에디션으로 →')
    expect(wrapper.get('[data-tier]').attributes('data-tier')).toBe('excellent')
  })
})
