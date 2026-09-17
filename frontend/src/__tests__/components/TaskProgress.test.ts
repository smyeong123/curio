import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskProgress from '@/components/studio/TaskProgress.vue'
import { i18n } from '@/i18n'
import type { StudioTask } from '@/types/studio'

const digest = (task: Partial<StudioTask>): StudioTask => ({ type: 'digest', state: 'IDLE', ...task })

describe('TaskProgress', () => {
  it('renders nothing for a missing or idle task', () => {
    expect(mount(TaskProgress).find('div').exists()).toBe(false)
    expect(mount(TaskProgress, { props: { task: digest({ state: 'IDLE' }) } }).find('div').exists()).toBe(false)
  })

  it('shows the phase with a determinate bar while running', () => {
    const w = mount(TaskProgress, {
      props: { task: digest({ state: 'RUNNING', phase: 'Fetching feeds', current: 2, total: 5 }) }
    })
    expect(w.get('p').text()).toBe('Fetching feeds (2/5)')
    expect(w.get('p').attributes('style')).toContain('var(--signal-deep)')
    expect(w.get('.transition-all').attributes('style')).toContain('width: 40%')
  })

  it('shows an indeterminate bar while queued without a total', () => {
    const w = mount(TaskProgress, { props: { task: digest({ state: 'QUEUED' }) } })
    expect(w.get('p').text()).toBe('Working…')
    expect(w.find('.animate-pulse').exists()).toBe(true)
  })

  it('shows the backend message, or the state label, once finished', () => {
    const done = mount(TaskProgress, { props: { task: digest({ state: 'SUCCESS' }) } })
    expect(done.get('p').text()).toBe('SUCCESS')
    expect(done.get('p').attributes('style')).toContain('var(--leaf)')
    expect(done.find('.bg-paper-deep').exists()).toBe(false)

    const failed = mount(TaskProgress, { props: { task: digest({ state: 'FAILED', message: 'Lost connection' }) } })
    expect(failed.get('p').text()).toBe('Lost connection')
  })

  it('labels bare states through the edition catalog', () => {
    i18n.global.locale.value = 'ko'
    const w = mount(TaskProgress, { props: { task: digest({ state: 'FAILED' }) } })
    expect(w.get('p').text()).toBe('실패')
  })
})
