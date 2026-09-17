import { describe, it, expect, vi } from 'vitest'
import { usePagedAdminList } from '@/composables/usePagedAdminList'

const pageOf = <T>(content: T[], number = 0, totalPages = 1) => ({
  data: { content, number, totalPages, totalElements: content.length, size: 20 },
})

describe('usePagedAdminList', () => {
  it('loads a page into items and records the envelope', async () => {
    const fetcher = vi.fn().mockResolvedValue(pageOf(['a', 'b'], 1, 3))
    const onError = vi.fn()
    const list = usePagedAdminList<string>(fetcher, onError)
    expect(list.loading.value).toBe(false)

    const pending = list.load(1)
    expect(list.loading.value).toBe(true)
    await pending

    expect(fetcher).toHaveBeenCalledWith(1)
    expect(list.items.value).toEqual(['a', 'b'])
    expect(list.page.value).toBe(1)
    expect(list.totalPages.value).toBe(3)
    expect(list.totalElements.value).toBe(2)
    expect(list.loading.value).toBe(false)
    expect(list.errorOccurred.value).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })

  it('defaults to the first page', async () => {
    const fetcher = vi.fn().mockResolvedValue(pageOf([]))
    await usePagedAdminList(fetcher, () => {}).load()
    expect(fetcher).toHaveBeenCalledWith(0)
  })

  it('flags the error, calls onError and clears it again on the next successful load', async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new Error('boom')).mockResolvedValue(pageOf(['a']))
    const onError = vi.fn()
    const list = usePagedAdminList<string>(fetcher, onError)

    await list.load()
    expect(list.errorOccurred.value).toBe(true)
    expect(list.loading.value).toBe(false)
    expect(onError).toHaveBeenCalledTimes(1)

    await list.load()
    expect(list.errorOccurred.value).toBe(false)
    expect(list.items.value).toEqual(['a'])
  })
})
