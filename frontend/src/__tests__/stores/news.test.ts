import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNewsStore } from '@/stores/news'

vi.mock('@/services/api', () => ({
  api: {
    news: {
      getDigests: vi.fn(),
      search: vi.fn()
    }
  }
}))

import { api } from '@/services/api'

const mockDigest = {
  id: 'digest-1',
  content: {
    summaries: [
      { headline: 'Test', summary: 'Summary', why_it_matters: 'Matters', source_url: 'https://example.com', source_name: 'Example', topic: 'Claude (Anthropic)' }
    ],
    generatedFor: ['Claude (Anthropic)']
  },
  generatedAt: '2026-03-15T06:00:00Z',
  emailSentAt: null
}

const digestOn = (page: number) => ({ ...mockDigest, id: `digest-p${page}` })

const pageOf = (content: unknown[], number: number, totalPages: number) => ({
  data: { content, totalPages, totalElements: content.length * totalPages, number, size: 50 }
})

describe('news store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with empty state', () => {
    const store = useNewsStore()
    expect(store.digests).toEqual([])
    expect(store.topicFilter).toBeNull()
  })

  it('fetchAllDigests loads a single-page archive with one request', async () => {
    vi.mocked(api.news.getDigests).mockResolvedValue(pageOf([mockDigest], 0, 1) as any)
    const store = useNewsStore()

    const all = await store.fetchAllDigests()

    expect(all).toEqual([mockDigest])
    expect(store.digests).toEqual([mockDigest])
    expect(api.news.getDigests).toHaveBeenCalledTimes(1)
    expect(api.news.getDigests).toHaveBeenCalledWith(0, 50)
  })

  it('fetchAllDigests requests the remaining pages together and keeps them in order', async () => {
    let releasePage1!: () => void
    vi.mocked(api.news.getDigests).mockImplementation(((page: number) => {
      if (page === 0) return Promise.resolve(pageOf([digestOn(0)], 0, 3))
      if (page === 1) return new Promise((resolve) => { releasePage1 = () => resolve(pageOf([digestOn(1)], 1, 3)) })
      return Promise.resolve(pageOf([digestOn(2)], 2, 3))
    }) as any)
    const store = useNewsStore()

    const pending = store.fetchAllDigests()
    await vi.waitFor(() => expect(api.news.getDigests).toHaveBeenCalledTimes(3))
    // Page 2 was requested without waiting for page 1 to land.
    expect(api.news.getDigests).toHaveBeenNthCalledWith(2, 1, 50)
    expect(api.news.getDigests).toHaveBeenNthCalledWith(3, 2, 50)

    releasePage1()
    const all = await pending

    expect(all.map((d) => d.id)).toEqual(['digest-p0', 'digest-p1', 'digest-p2'])
    expect(store.digests.map((d) => d.id)).toEqual(['digest-p0', 'digest-p1', 'digest-p2'])
  })

  it('fetchAllDigests stops at maxPages', async () => {
    vi.mocked(api.news.getDigests).mockImplementation(((page: number) =>
      Promise.resolve(pageOf([digestOn(page)], page, 40))) as any)
    const store = useNewsStore()

    const all = await store.fetchAllDigests(50, 2)

    expect(api.news.getDigests).toHaveBeenCalledTimes(2)
    expect(all).toHaveLength(2)
  })

  it('fetchAllDigests handles an empty archive (zero pages)', async () => {
    vi.mocked(api.news.getDigests).mockResolvedValue(pageOf([], 0, 0) as any)
    const store = useNewsStore()

    const all = await store.fetchAllDigests()

    expect(all).toEqual([])
    expect(api.news.getDigests).toHaveBeenCalledTimes(1)
  })

  it('searchDigests returns results without touching browse state', async () => {
    vi.mocked(api.news.search).mockResolvedValue(pageOf([mockDigest], 0, 1) as any)
    const store = useNewsStore()

    const page = await store.searchDigests('claude')

    expect(page.content).toEqual([mockDigest])
    expect(store.digests).toEqual([])
    expect(api.news.search).toHaveBeenCalledWith('claude', 0, 50)
  })

  it('reset wipes news state (logout must not leak across accounts)', async () => {
    vi.mocked(api.news.getDigests).mockResolvedValue(pageOf([mockDigest], 0, 1) as any)
    const store = useNewsStore()
    await store.fetchAllDigests()
    store.topicFilter = 'Claude (Anthropic)'

    store.reset()

    expect(store.digests).toEqual([])
    expect(store.topicFilter).toBeNull()
  })

  it('a stale fetchAllDigests response does not clobber a newer one', async () => {
    let resolveStale: (v: unknown) => void
    const stalePromise = new Promise((resolve) => { resolveStale = resolve })
    vi.mocked(api.news.getDigests)
      .mockReturnValueOnce(stalePromise as any)
      .mockResolvedValueOnce(pageOf([{ ...mockDigest, id: 'digest-fresh' }], 0, 1) as any)
    const store = useNewsStore()

    const first = store.fetchAllDigests()
    await store.fetchAllDigests()
    resolveStale!(pageOf([{ ...mockDigest, id: 'digest-stale' }], 0, 1))
    await first

    expect(store.digests.map((d) => d.id)).toEqual(['digest-fresh'])
  })
})
