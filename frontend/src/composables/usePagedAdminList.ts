import { ref, type Ref } from 'vue'
import type { SpringPage } from '@/types/page'

/**
 * Loading and page state shared by the admin roster, ledger and editions
 * tables: one `load(page)` that swaps the rows in and records the page
 * envelope. Each view keeps its own template; `fetcher` closes over the
 * view's filters, and `onError` is the view's toast.
 */
export function usePagedAdminList<T>(
  fetcher: (page: number) => Promise<{ data: SpringPage<T> }>,
  onError: () => void
) {
  const items = ref<T[]>([]) as Ref<T[]>
  const page = ref(0)
  const totalPages = ref(0)
  const totalElements = ref(0)
  const loading = ref(false)
  const errorOccurred = ref(false)

  const load = async (nextPage = 0) => {
    loading.value = true
    errorOccurred.value = false
    try {
      const { data } = await fetcher(nextPage)
      items.value = data.content
      page.value = data.number
      totalPages.value = data.totalPages
      totalElements.value = data.totalElements
    } catch {
      errorOccurred.value = true
      onError()
    } finally {
      loading.value = false
    }
  }

  return { items, page, totalPages, totalElements, loading, errorOccurred, load }
}
