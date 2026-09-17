<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead :kicker="t('archive.header.kicker')" keypath="archive.header.headline" emphasis="brand">
      <template #emphasis>Curio</template>
      <template #aside>
        <div class="text-right">
          <p class="deco-num text-[44px] leading-none">{{ readableNumber }}</p>
          <p class="kicker mt-1">{{ t('archive.header.issuesInArchive') }}</p>
        </div>
      </template>
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] mt-4 leading-relaxed max-w-[64ch]">
        {{ t('archive.header.intro') }}
      </p>
    </PageMasthead>

    <ErrorState
      v-if="errorOccurred"
      :kicker="t('archive.error.kicker')"
      :headline="t('archive.error.headline')"
      :body="t('archive.error.body')"
      @retry="load"
    />

    <!-- ── Loading skeleton ─────────────────────────────────── -->
    <section v-else-if="loading" class="space-y-8">
      <div v-for="i in 3" :key="i" class="border-t border-[color:var(--rule)] pt-6 animate-pulse">
        <div class="h-3 w-24 bg-paper-deep mb-5"></div>
        <div class="h-8 w-3/4 bg-paper-deep mb-3"></div>
        <div class="h-3 w-full bg-paper-deep mb-2"></div>
        <div class="h-3 w-2/3 bg-paper-deep"></div>
      </div>
    </section>

    <!-- ── Empty state (no editions in the last 7 days) ─────────── -->
    <section v-else-if="recentDigests.length === 0" class="border-2 border-[color:var(--rule)] bg-paper-deep p-14 text-center">
      <p class="kicker mb-5">{{ t('archive.empty.kicker') }}</p>
      <i18n-t scope="global" keypath="archive.empty.headline" tag="h3" class="display-headline text-[36px] leading-tight mb-3">
        <template #tail><em class="italic-display">{{ t('archive.empty.headlineTail') }}</em></template>
      </i18n-t>
      <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-7 max-w-md mx-auto">
        {{ t('archive.empty.body') }}
      </p>
      <router-link to="/onboarding" class="btn-editorial">
        {{ t('archive.empty.cta') }}
        <span aria-hidden="true">→</span>
      </router-link>
    </section>

    <!-- ── Digest feed ──────────────────────────────────────── -->
    <section v-else class="space-y-12">
      <ArchiveSearch
        v-model:query="searchQuery"
        :loading="searchLoading"
        :active="searchActive"
        :match-count="searchMatchCount"
        @clear="clearSearch"
      />

      <BeatFilter
        :groups="groupedTopics"
        :active-topic="activeTopicFilter"
        :total-active="totalActiveBeats"
        @select="selectTopic"
      />

      <!-- No issues match the search and/or active beat (archive itself is non-empty) -->
      <div
        v-if="filteredDigests.length === 0 && !searchLoading"
        class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center"
      >
        <template v-if="searchActive">
          <p class="kicker mb-3">{{ t('archive.noMatches.kicker') }}</p>
          <i18n-t
            scope="global"
            :keypath="activeTopicFilter ? 'archive.noMatches.bodyOnTopic' : 'archive.noMatches.body'"
            tag="p"
            class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 max-w-md mx-auto"
          >
            <template #query><strong>"{{ searchQuery.trim() }}"</strong></template>
            <template #topic><strong>{{ topicLabel(activeTopicFilter) }}</strong></template>
          </i18n-t>
          <button class="btn-editorial-ghost" @click="clearSearch">{{ t('archive.noMatches.clear') }}</button>
        </template>
        <template v-else>
          <p class="kicker mb-3">{{ t('archive.noBeat.kicker') }}</p>
          <i18n-t scope="global" keypath="archive.noBeat.body" tag="p" class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 max-w-md mx-auto">
            <template #topic><strong>{{ topicLabel(activeTopicFilter) }}</strong></template>
          </i18n-t>
          <button class="btn-editorial-ghost" @click="selectTopic('')">{{ t('archive.noBeat.showAll') }}</button>
        </template>
      </div>

      <!-- Current day heading — one day per page, within the last 7 days -->
      <div v-if="currentDay" class="flex items-baseline justify-between gap-3 flex-wrap">
        <p class="kicker kicker-signal">{{ currentDay.label }}</p>
        <p class="kicker text-[color:var(--mute)]">
          {{ t('archive.paging.dayOf', { page: localPage + 1, total: totalPages }) }} · {{ searchActive ? t('archive.paging.searchResults') : t('archive.paging.lastDays') }}
        </p>
      </div>

      <DigestIssue
        v-for="(issue, idx) in pagedIssues"
        :key="issue.digest.id"
        :digest="issue.digest"
        :stories="issue.stories"
        :topics="issue.topics"
        :issue-number="issue.issueNumber"
        :dropcap="idx === 0"
      />

      <!-- Pagination — one day per page (most recent first, last 7 days only) -->
      <PagerNav
        v-if="totalPages > 1"
        class="border-t-2 border-[color:var(--rule)] pt-6"
        :page="localPage"
        :total-pages="totalPages"
        :newer-label="t('archive.paging.newer')"
        :older-label="t('archive.paging.older')"
        counter-keypath="archive.paging.dayOf"
        @change="goToPage"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useNewsStore } from '@/stores/news'
import { useToast } from '@/composables/useToast'
import { useFormat } from '@/composables/useFormat'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { TOPIC_HIERARCHY, TOPIC_DOMAIN_MAP } from '@/data/topics'
import type { Digest, NewsSummary } from '@/types/news'
import { getApiErrorMessage } from '@/utils/apiError'
import PageMasthead from '@/components/ui/PageMasthead.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PagerNav from '@/components/ui/PagerNav.vue'
import ArchiveSearch from '@/components/archive/ArchiveSearch.vue'
import BeatFilter from '@/components/archive/BeatFilter.vue'
import DigestIssue from '@/components/archive/DigestIssue.vue'
import type { TopicGroup, TopicItem } from '@/components/archive/BeatFilter.vue'

const newsStore = useNewsStore()
const { digests, topicFilter } = storeToRefs(newsStore)
const { error: showError } = useToast()
const { t } = useI18n()
const { formatDate } = useFormat()
// Topic names are canonical ids (backend-stored); label them per edition at render time.
const { topicLabel, groupName } = useTopicLabels()

const loading = ref(true)
const errorOccurred = ref(false)
// Restore the last-used beat filter (persisted by the store across reloads).
const activeTopicFilter = ref(topicFilter.value ?? '')

// Users can only see the last 7 days of editions; older issues are hidden.
const DAYS_WINDOW = 7
const DAY_MS = 24 * 60 * 60 * 1000
const localPage = ref(0) // index into dayGroups (0 = most recent day)

// Digests within the last 7 days (rolling window) — the only ones the user sees.
const recentDigests = computed(() => {
  const cutoff = Date.now() - DAYS_WINDOW * DAY_MS
  return digests.value.filter((d) => new Date(d.generatedAt).getTime() >= cutoff)
})

const readableNumber = computed(() => String(recentDigests.value.length).padStart(2, '0'))

// ── Archive search — server-side FTS over the whole retained archive ──────
// Debounced; results replace the 7-day browse set in the same rendering
// pipeline (beat filter + day-grouped pagination still apply on top).
const searchQuery = ref('')
const searchResults = ref<Digest[]>([])
const searchLoading = ref(false)
const searchActive = computed(() => searchQuery.value.trim().length >= 2)
let searchTimer: ReturnType<typeof setTimeout> | undefined
let searchSeq = 0 // drops out-of-order responses from stale keystrokes

watch(searchQuery, (q) => {
  clearTimeout(searchTimer)
  const query = q.trim()
  if (query.length < 2) {
    searchSeq++
    searchResults.value = []
    searchLoading.value = false
    localPage.value = 0
    return
  }
  searchLoading.value = true
  searchTimer = setTimeout(async () => {
    const seq = ++searchSeq
    try {
      const page = await newsStore.searchDigests(query)
      if (seq !== searchSeq) return
      searchResults.value = page.content
      localPage.value = 0
    } catch (err: unknown) {
      if (seq !== searchSeq) return
      searchResults.value = []
      showError(getApiErrorMessage(err, t('archive.errors.search')))
    } finally {
      if (seq === searchSeq) searchLoading.value = false
    }
  }, 300)
})

const clearSearch = () => {
  searchQuery.value = ''
}

// The server's FTS matches whole digests, so a one-story hit would otherwise
// drag its 7 sibling stories along. Narrow to the matching stories client-side:
// every query term must appear somewhere in the story's text (case-insensitive).
const searchTerms = computed(() =>
  searchActive.value
    ? searchQuery.value.trim().toLowerCase().split(/\s+/).filter((t) => t.length >= 2)
    : []
)

const storyMatchesSearch = (s: NewsSummary) => {
  if (searchTerms.value.length === 0) return true
  const haystack =
    `${s.headline} ${s.summary} ${s.why_it_matters ?? ''} ${s.topic} ${s.source_name ?? ''}`.toLowerCase()
  return searchTerms.value.every((t) => haystack.includes(t))
}

// What the feed below renders: search results when a query is active,
// otherwise the last-7-days browse window.
const baseDigests = computed(() => (searchActive.value ? searchResults.value : recentDigests.value))

const selectTopic = (topic: string) => {
  activeTopicFilter.value = topic
  localPage.value = 0
}

// Persist the active filter so it survives a reload / navigation away.
watch(activeTopicFilter, (value) => {
  topicFilter.value = value || null
})

// How many visible *stories* cover each beat (by each summary's stamped topic).
// Counting stories, not digests, so the chip number matches what you actually
// see when you filter — a single daily edition covers several beats at once.
// Scoped to what's viewable (last-7-days window, or search results).
const topicCounts = computed<Record<string, number>>(() => {
  const counts: Record<string, number> = {}
  for (const digest of baseDigests.value) {
    for (const summary of digest.content?.summaries ?? []) {
      const topic = summary.topic
      if (topic) counts[topic] = (counts[topic] ?? 0) + 1
    }
  }
  return counts
})

// The full taxonomy, grouped by big topic (L1), mirroring the admin Beat
// distribution: every domain and every leaf is shown, with a per-beat story
// count. Beats with no stories render disabled/greyed.
const groupedTopics = computed<TopicGroup[]>(() => {
  const counts = topicCounts.value

  const groups: TopicGroup[] = TOPIC_HIERARCHY.map((domain) => {
    const topics: TopicItem[] = domain.subcategories
      .flatMap((sub) => sub.topics)
      .map((name) => ({ name, count: counts[name] ?? 0 }))
    return {
      id: domain.id,
      name: groupName(domain),
      icon: domain.icon,
      topics,
      activeCount: topics.filter((t) => t.count > 0).length,
    }
  })

  // Any beat not in the taxonomy (e.g. legacy digests) falls under "Other".
  const others = Object.keys(counts)
    .filter((topic) => !(topic in TOPIC_DOMAIN_MAP))
    .sort()
  if (others.length > 0) {
    const topics: TopicItem[] = others.map((name) => ({ name, count: counts[name] ?? 0 }))
    groups.push({
      id: '__other',
      name: t('archive.filter.other'),
      icon: 'archive',
      topics,
      activeCount: topics.filter((t) => t.count > 0).length,
    })
  }

  return groups
})

// Total selectable beats — shown as a hint on the collapsed filter header.
const totalActiveBeats = computed(() =>
  groupedTopics.value.reduce((total, group) => total + group.activeCount, 0)
)

// A digest is shown only if it has at least one story on the active beat, so
// filtering an all-beats daily edition narrows it instead of showing everything.
const filteredDigests = computed(() =>
  activeTopicFilter.value
    ? baseDigests.value.filter((d) =>
        (d.content?.summaries ?? []).some((s) => s.topic === activeTopicFilter.value)
      )
    : baseDigests.value
)

// The stories shown for a digest: narrowed by the active beat and, during a
// search, to the stories that actually match the query.
const visibleSummaries = (digest: Digest) => {
  const all = digest.content?.summaries ?? []
  const beatFiltered = activeTopicFilter.value
    ? all.filter((s) => s.topic === activeTopicFilter.value)
    : all
  if (searchTerms.value.length === 0) return beatFiltered
  const matched = beatFiltered.filter(storyMatchesSearch)
  // The server matched this digest via stemmed FTS ("regulate" ~ "regulation"),
  // so an exact-substring miss here can be a stemming artifact — fall back to
  // the whole (beat-filtered) edition rather than rendering an empty issue.
  return matched.length > 0 ? matched : beatFiltered
}

// Stable issue number based on digest id (last 3 chars hex → decimal-ish).
const issueNumberFor = (id: string) => {
  const slice = id.slice(-3)
  const n = parseInt(slice, 16)
  return Number.isFinite(n) ? String(n).padStart(3, '0') : '???'
}

// Each visible digest with its narrowed stories, resolved once per render so
// the day grouping, the match count and the issue cards share the same lists.
interface Issue {
  digest: Digest
  topics: string[]
  stories: NewsSummary[]
  issueNumber: string
}

const issues = computed<Issue[]>(() =>
  filteredDigests.value.map((digest) => ({
    digest,
    topics: digest.content?.generatedFor ?? [],
    stories: visibleSummaries(digest),
    issueNumber: issueNumberFor(digest.id),
  }))
)

// Stories the active search will actually render, so the results copy counts
// what the reader sees, not whole issues.
const searchMatchCount = computed(() =>
  issues.value.reduce((n, issue) => n + issue.stories.length, 0)
)

// Group the visible issues by calendar day, newest first. Each page of the
// archive is one day's edition(s) — "pagination for each day".
interface DayGroup {
  key: string
  label: string
  issues: Issue[]
}

const dayKey = (dateStr: string) => {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const dayGroups = computed<DayGroup[]>(() => {
  const map = new Map<string, Issue[]>()
  for (const issue of issues.value) {
    const k = dayKey(issue.digest.generatedAt)
    const bucket = map.get(k)
    if (bucket) bucket.push(issue)
    else map.set(k, [issue])
  }
  return [...map.entries()]
    .sort((a, b) => (a[0] < b[0] ? 1 : -1)) // newest day first
    .map(([key, group]) => ({
      key,
      label: formatDate(group[0]!.digest.generatedAt, 'full'),
      issues: [...group].sort(
        (x, y) => new Date(y.digest.generatedAt).getTime() - new Date(x.digest.generatedAt).getTime()
      ),
    }))
})

const totalPages = computed(() => Math.max(1, dayGroups.value.length))

const currentDay = computed(() => dayGroups.value[localPage.value] ?? null)

const pagedIssues = computed(() => currentDay.value?.issues ?? [])

// Keep the current page valid if the day count shrinks (filter change / reload).
watch(totalPages, (n) => {
  if (localPage.value >= n) localPage.value = 0
})

const goToPage = (page: number) => {
  if (page < 0 || page >= totalPages.value) return
  localPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const load = async () => {
  loading.value = true
  errorOccurred.value = false
  try {
    await newsStore.fetchAllDigests()
    if (localPage.value >= totalPages.value) localPage.value = 0
  } catch (err: unknown) {
    errorOccurred.value = true
    showError(getApiErrorMessage(err, t('archive.errors.load')))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
