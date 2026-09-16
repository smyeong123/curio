import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNewsStore } from '@/stores/news'

vi.mock('@/services/api', () => ({
  api: {
    news: {
      getDigests: vi.fn(),
      getDigest: vi.fn(),
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

describe('news store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with empty state', () => {
    const store = useNewsStore()
    expect(store.digests).toEqual([])
    expect(store.currentDigest).toBeNull()
    expect(store.totalPages).toBe(0)
    expect(store.currentPage).toBe(0)
  })

  it('fetchDigests sets digests and pagination', async () => {
    vi.mocked(api.news.getDigests).mockResolvedValue({
      data: { content: [mockDigest], totalPages: 3, totalElements: 25, number: 1, size: 10 }
    } as any)
    const store = useNewsStore()

    await store.fetchDigests(1, 10)

    expect(store.digests).toEqual([mockDigest])
    expect(store.totalPages).toBe(3)
    expect(store.currentPage).toBe(1)
    expect(api.news.getDigests).toHaveBeenCalledWith(1, 10)
  })

  it('fetchDigest sets currentDigest', async () => {
    vi.mocked(api.news.getDigest).mockResolvedValue({ data: mockDigest } as any)
    const store = useNewsStore()

    await store.fetchDigest('digest-1')

    expect(store.currentDigest).toEqual(mockDigest)
    expect(api.news.getDigest).toHaveBeenCalledWith('digest-1')
  })

  it('searchDigests returns results without touching browse state', async () => {
    vi.mocked(api.news.search).mockResolvedValue({
      data: { content: [mockDigest], totalPages: 1, totalElements: 1, number: 0, size: 50 }
    } as any)
    const store = useNewsStore()

    const page = await store.searchDigests('claude')

    expect(page.content).toEqual([mockDigest])
    expect(store.digests).toEqual([])
    expect(api.news.search).toHaveBeenCalledWith('claude', 0, 50)
  })

  it('fetchDigests defaults to page 0 size 10', async () => {
    vi.mocked(api.news.getDigests).mockResolvedValue({
      data: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 10 }
    } as any)
    const store = useNewsStore()

    await store.fetchDigests()

    expect(api.news.getDigests).toHaveBeenCalledWith(0, 10)
  })

  it('reset wipes news state (logout must not leak across accounts)', async () => {
    vi.mocked(api.news.getDigest).mockResolvedValue({ data: mockDigest } as any)
    vi.mocked(api.news.getDigests).mockResolvedValue({
      data: { content: [mockDigest], totalPages: 1, totalElements: 1, number: 0, size: 10 }
    } as any)
    const store = useNewsStore()
    await store.fetchDigests()
    await store.fetchDigest('digest-1')
    store.topicFilter = 'Claude (Anthropic)'

    store.reset()

    expect(store.digests).toEqual([])
    expect(store.currentDigest).toBeNull()
    expect(store.totalPages).toBe(0)
    expect(store.currentPage).toBe(0)
    expect(store.topicFilter).toBeNull()
  })

  it('a stale fetchDigest response does not clobber a newer one', async () => {
    const staleDigest = { ...mockDigest, id: 'digest-stale' }
    const freshDigest = { ...mockDigest, id: 'digest-fresh' }
    let resolveStale: (v: unknown) => void
    const stalePromise = new Promise((resolve) => { resolveStale = resolve })
    vi.mocked(api.news.getDigest)
      .mockReturnValueOnce(stalePromise as any)
      .mockResolvedValueOnce({ data: freshDigest } as any)
    const store = useNewsStore()

    const first = store.fetchDigest('digest-stale')
    await store.fetchDigest('digest-fresh')
    resolveStale!({ data: staleDigest })
    await first

    expect(store.currentDigest?.id).toBe('digest-fresh')
  })
})
