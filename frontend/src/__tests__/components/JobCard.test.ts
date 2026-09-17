import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import JobCard from '@/components/admin/JobCard.vue'

const baseProps = {
  title: 'Generate digests',
  cron: 'Cron · 06:00 UTC',
  body: 'Generate AI news digests.',
  doneLabel: 'Filed',
  busy: false,
  busyLabel: 'Running…',
  resultText: '4 / 5 users · 1 failed',
}

const idle = { running: false, lastRanAt: null, lastResult: null }

describe('JobCard', () => {
  it('renders the title, cron line and body with no badge or result for a job that never ran', () => {
    const w = mount(JobCard, { props: { ...baseProps, state: idle } })
    const text = w.text()
    expect(text).toContain('Generate digests')
    expect(text).toContain('Cron · 06:00 UTC')
    expect(text).toContain('Generate AI news digests.')
    expect(text).toContain('Run now')
    expect(text).not.toContain('Last ran')
    expect(text).not.toContain('4 / 5 users')
    expect(w.find('span.animate-pulse').exists()).toBe(false)
    expect(w.get('button').attributes('disabled')).toBeUndefined()
  })

  it('shows the running badge and busy label, and disables the button, while running', () => {
    const w = mount(JobCard, { props: { ...baseProps, state: { ...idle, running: true } } })
    expect(w.find('span.animate-pulse').exists()).toBe(true)
    expect(w.get('button').text()).toContain('Running…')
    expect(w.get('button').attributes('disabled')).toBeDefined()
  })

  it('disables the button with the busy label while the trigger request is in flight', () => {
    const w = mount(JobCard, { props: { ...baseProps, busy: true, busyLabel: 'Sending…', state: idle } })
    expect(w.get('button').text()).toContain('Sending…')
    expect(w.get('button').attributes('disabled')).toBeDefined()
  })

  it('shows the done badge, last-ran line and result text after a successful run', () => {
    const w = mount(JobCard, {
      props: { ...baseProps, state: { running: false, lastRanAt: '2026-09-16T12:00:00Z', lastResult: { digestSuccess: 4 } } },
    })
    const text = w.text()
    expect(text).toContain('● Filed')
    expect(text).toContain('Last ran · Sep 16, 2026')
    expect(text).toContain('4 / 5 users · 1 failed')
  })

  it('renders the optional detail line under the result', () => {
    const state = { running: false, lastRanAt: '2026-09-16T12:00:00Z', lastResult: { digestSuccess: 4 } }
    const withDetail = mount(JobCard, { props: { ...baseProps, state, detailText: 'Quizzes · 4 filed · 0 failed · Took 12.3 sec' } })
    expect(withDetail.text()).toContain('Quizzes · 4 filed · 0 failed · Took 12.3 sec')
    const without = mount(JobCard, { props: { ...baseProps, state } })
    expect(without.findAll('p.kicker').map((p) => p.text())).not.toContain('')
    expect(without.text()).not.toContain('Quizzes')
  })

  it('shows the failed badge when the last run failed', () => {
    const w = mount(JobCard, {
      props: { ...baseProps, resultText: 'Job failed', state: { running: false, lastRanAt: null, lastResult: { failed: true } } },
    })
    expect(w.text()).toContain('● Failed')
    expect(w.text()).not.toContain('● Filed')
  })

  it('renders the controls and errors slots and emits run on click', async () => {
    const w = mount(JobCard, {
      props: { ...baseProps, state: idle },
      slots: { controls: '<select id="beat"><option>All</option></select>', errors: '<div class="why">Why it failed</div>' },
    })
    expect(w.find('#beat').exists()).toBe(true)
    expect(w.get('.why').text()).toBe('Why it failed')
    // The body keeps its gap above the controls only when there are controls.
    expect(w.get('p.font-body-curio').classes()).toContain('mb-3')
    const bare = mount(JobCard, { props: { ...baseProps, state: idle } })
    expect(bare.get('p.font-body-curio').classes()).not.toContain('mb-3')
    await w.get('button').trigger('click')
    expect(w.emitted('run')).toHaveLength(1)
  })

  it('uses the destructive button styling and glyph when danger is set', () => {
    const w = mount(JobCard, { props: { ...baseProps, danger: true, state: idle } })
    expect(w.get('button').classes()).toContain('text-red-600')
    expect(w.get('button').text()).toContain('↗')
    const plain = mount(JobCard, { props: { ...baseProps, state: idle } })
    expect(plain.get('button').classes()).toContain('btn-editorial')
    expect(plain.get('button').text()).toContain('→')
  })
})
