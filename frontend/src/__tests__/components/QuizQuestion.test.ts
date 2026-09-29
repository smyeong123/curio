import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import QuizQuestion from '@/components/quiz/QuizQuestion.vue'

const mockQuestion = {
  id: 1,
  question: 'What is the capital of France?',
  options: { A: 'London', B: 'Paris', C: 'Berlin', D: 'Madrid' }
}

describe('QuizQuestion', () => {
  it('renders question text', () => {
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: false }
    })
    expect(wrapper.text()).toContain('What is the capital of France?')
  })

  it('renders all options', () => {
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: false }
    })
    expect(wrapper.text()).toContain('London')
    expect(wrapper.text()).toContain('Paris')
    expect(wrapper.text()).toContain('Berlin')
    expect(wrapper.text()).toContain('Madrid')
  })

  it('emits select when option clicked', async () => {
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: false }
    })
    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click') // B = Paris
    expect(wrapper.emitted('select')?.[0]).toEqual([1, 'B'])
  })

  it('disables options when submitted', () => {
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: true }
    })
    const buttons = wrapper.findAll('button')
    buttons.forEach(btn => {
      expect(btn.attributes('disabled')).toBeDefined()
    })
  })

  it('shows feedback when submitted with result', () => {
    const result = { questionId: 1, correct: true, correctAnswer: 'B', explanation: 'Paris is the capital.' }
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: true, selectedAnswer: 'B', result }
    })
    // Verdict and explanation read as two sentences: no doubled punctuation, one space.
    expect(wrapper.text()).toContain('Correct! Paris is the capital.')
  })

  it('shows incorrect feedback', () => {
    const result = { questionId: 1, correct: false, correctAnswer: 'B', explanation: 'Paris is the capital.' }
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: true, selectedAnswer: 'A', result }
    })
    expect(wrapper.text()).toContain('Incorrect. Paris is the capital.')
  })

  it('highlights selected answer before submission', () => {
    const wrapper = mount(QuizQuestion, {
      props: { question: mockQuestion, total: 5, submitted: false, selectedAnswer: 'B' }
    })
    const buttons = wrapper.findAll('button')
    // Selected option carries the data-selected attribute regardless of styling.
    expect(buttons[1].attributes('data-selected')).toBe('true')
    expect(buttons[0].attributes('data-selected')).toBe('false')
  })
})
