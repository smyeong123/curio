import { describe, it, expect } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import DigestIssue from '@/components/archive/DigestIssue.vue'
import type { Digest, NewsSummary } from '@/types/news'

const story = (headline: string, overrides: Partial<NewsSummary> = {}): NewsSummary => ({
  headline,
  summary: `${headline} in brief.`,
  why_it_matters: 'It matters.',
  source_url: 'https://example.com/story',
  source_name: 'Tech Daily',
  topic: 'Claude (Anthropic)',
  ...overrides
})

// Local noon so the formatted date is the same calendar day in any timezone.
const generatedAt = new Date(2026, 8, 16, 12, 0).toISOString()

const digest: Digest = {
  id: 'digest-0a1',
  content: {
    summaries: [
      story('Claude gains longer context windows'),
      story('Mistral ships a new model', { topic: 'Mistral', why_it_matters: '', source_url: 'https://example.com/mistral', source_name: '' }),
      story('A poisoned source', { source_url: 'javascript:alert(1)' })
    ],
    generatedFor: ['Claude (Anthropic)', 'Mistral'],
    language: 'ko'
  },
  generatedAt,
  emailSentAt: generatedAt
}

const mountIssue = (props: Partial<InstanceType<typeof DigestIssue>['$props']> = {}) =>
  mount(DigestIssue, {
    props: {
      digest,
      stories: digest.content.summaries,
      topics: digest.content.generatedFor,
      issueNumber: '161',
      dropcap: true,
      ...props
    },
    global: { stubs: { RouterLink: RouterLinkStub } }
  })

describe('DigestIssue', () => {
  it('renders the masthead: issue number, date, delivery, quiz link and beats', () => {
    const w = mountIssue()
    expect(w.get('article').attributes('lang')).toBe('ko')
    const text = w.text()
    expect(text).toContain('Issue 161')
    expect(text).toContain('Wednesday, September 16, 2026')
    expect(text).toContain('Delivered')
    expect(text).toContain('Take quiz')
    expect(w.getComponent(RouterLinkStub).props('to')).toEqual({ name: 'quiz', params: { digestId: 'digest-0a1' } })
    expect(text).toContain('Beats')
    expect(text).toContain('Mistral')
    expect(text).toContain('— End of issue —')
  })

  it('numbers the stories, drop-caps only the lead story and links only safe sources', () => {
    const w = mountIssue()
    const items = w.findAll('ol > li')
    expect(items).toHaveLength(3)
    expect(items[0]!.text()).toContain('01')
    expect(items[2]!.text()).toContain('03')
    expect(items[0]!.find('p.dropcap').exists()).toBe(true)
    expect(items[1]!.find('p.dropcap').exists()).toBe(false)

    expect(items[0]!.text()).toContain('Why it matters')
    expect(items[0]!.get('a').attributes('href')).toBe('https://example.com/story')
    expect(items[0]!.get('a').attributes('rel')).toBe('noopener noreferrer')
    expect(items[0]!.text()).toContain('Source: Tech Daily')

    expect(items[1]!.text()).not.toContain('Why it matters')
    expect(items[1]!.text()).toContain('Source: link')

    expect(items[2]!.find('a').exists()).toBe(false)
  })

  it('renders no drop cap when the issue is not the page lead', () => {
    const w = mountIssue({ dropcap: false })
    expect(w.find('p.dropcap').exists()).toBe(false)
  })

  it('renders only the stories it is given and hides the beats row when empty', () => {
    const w = mountIssue({ stories: [digest.content.summaries[1]!], topics: [] })
    expect(w.findAll('ol > li')).toHaveLength(1)
    expect(w.text()).toContain('Mistral ships a new model')
    expect(w.text()).not.toContain('Claude gains longer context windows')
    expect(w.text()).not.toContain('Beats')
  })

  it('omits the delivery mark when the issue was never emailed', () => {
    const w = mountIssue({ digest: { ...digest, emailSentAt: null } })
    expect(w.text()).not.toContain('Delivered')
  })
})
