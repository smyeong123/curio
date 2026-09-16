import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api'
import type { Digest } from '@/types/news'

export const useNewsStore = defineStore('news', () => {
  const digests = ref<Digest[]>([])
  const currentDigest = ref<Digest | null>(null)
  const totalPages = ref(0)
  const currentPage = ref(0)
  const topicFilter = ref<string | null>(null)

  // Monotonic sequence guards: a slow response from an earlier call must not
  // clobber shared store state after a newer call has already resolved (e.g.
  // rapidly opening digest A then B while A's GET is still in flight).
  let digestSeq = 0
  let listSeq = 0

  const fetchDigests = async (page = 0, size = 10) => {
    const seq = ++listSeq
    const response = await api.news.getDigests(page, size)
    if (seq === listSeq) {
      digests.value = response.data.content
      totalPages.value = response.data.totalPages
      currentPage.value = page
    }
    return response.data
  }

  // Load the user's ENTIRE archive (bounded by the 30-day retention window, so a
  // handful of pages at most) so the Archive view can count, filter, and paginate
  // over the whole set client-side instead of one server page at a time.
  const fetchAllDigests = async (size = 50, maxPages = 20) => {
    const seq = ++listSeq
    const first = await api.news.getDigests(0, size)
    let all = [...first.data.content]
    const pages = Math.min(first.data.totalPages, maxPages)
    for (let p = 1; p < pages; p++) {
      const resp = await api.news.getDigests(p, size)
      all = all.concat(resp.data.content)
    }
    if (seq === listSeq) {
      digests.value = all
      totalPages.value = 1
      currentPage.value = 0
    }
    return all
  }

  const fetchDigest = async (id: string) => {
    const seq = ++digestSeq
    const response = await api.news.getDigest(id)
    if (seq === digestSeq) {
      currentDigest.value = response.data
    }
    return response.data
  }

  // Server-side full-text search over the user's whole retained archive.
  // Returns results without touching `digests` so the browse view stays intact.
  const searchDigests = async (query: string, page = 0, size = 50) => {
    const response = await api.news.search(query, page, size)
    return response.data
  }

  // Wipe all in-memory state on logout so the next account on this browser
  // never sees a flash of the previous user's archive. Bumping the sequence
  // counters also invalidates any responses still in flight.
  const reset = () => {
    digestSeq++
    listSeq++
    digests.value = []
    currentDigest.value = null
    totalPages.value = 0
    currentPage.value = 0
    topicFilter.value = null
  }

  return {
    digests,
    currentDigest,
    totalPages,
    currentPage,
    topicFilter,
    fetchDigests,
    fetchAllDigests,
    fetchDigest,
    searchDigests,
    reset
  }
}, {
  persist: { paths: ['topicFilter'] }
})
