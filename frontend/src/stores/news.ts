import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api'
import type { Digest } from '@/types/news'

export const useNewsStore = defineStore('news', () => {
  const digests = ref<Digest[]>([])
  const topicFilter = ref<string | null>(null)

  // Monotonic sequence guard: a slow response from an earlier load must not
  // clobber the archive after a newer load (or a logout reset) has resolved.
  let listSeq = 0

  // Load the user's ENTIRE archive (bounded by the 30-day retention window, so a
  // handful of pages at most) so the Archive view can count, filter, and paginate
  // over the whole set client-side instead of one server page at a time. Page 0
  // reveals the page count; the rest are requested together and kept in order.
  const fetchAllDigests = async (size = 50, maxPages = 20) => {
    const seq = ++listSeq
    const first = await api.news.getDigests(0, size)
    const pages = Math.min(first.data.totalPages, maxPages)
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, pages - 1) }, (_, i) => api.news.getDigests(i + 1, size))
    )
    const all = first.data.content.concat(...rest.map((r) => r.data.content))
    if (seq === listSeq) {
      digests.value = all
    }
    return all
  }

  // Server-side full-text search over the user's whole retained archive.
  // Returns results without touching `digests` so the browse view stays intact.
  const searchDigests = async (query: string, page = 0, size = 50) => {
    const response = await api.news.search(query, page, size)
    return response.data
  }

  // Wipe all in-memory state on logout so the next account on this browser
  // never sees a flash of the previous user's archive. Bumping the sequence
  // counter also invalidates any response still in flight.
  const reset = () => {
    listSeq++
    digests.value = []
    topicFilter.value = null
  }

  return {
    digests,
    topicFilter,
    fetchAllDigests,
    searchDigests,
    reset
  }
}, {
  persist: { paths: ['topicFilter'] }
})
