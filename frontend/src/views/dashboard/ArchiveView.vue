<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Edition header ─────────────────────────────────────── -->
    <header class="mb-10">
      <div class="flex items-end justify-between flex-wrap gap-4 mb-6">
        <div>
          <p class="kicker kicker-signal mb-3">Your edition</p>
          <h1 class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95]">
            Today on
            <em class="italic-display">Curio</em>.
          </h1>
        </div>
        <div class="text-right">
          <p class="deco-num text-[44px] leading-none">{{ readableNumber }}</p>
          <p class="kicker mt-1">Issues in archive</p>
        </div>
      </div>
      <div class="rule-double w-full"></div>
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] mt-4 leading-relaxed max-w-[64ch]">
        Your complete archive of personalized digests.
        Each issue files the day's model-news stories on the topics you've picked, then
        quizzes you on what stuck.
      </p>
    </header>

    <!-- ── Error state ──────────────────────────────────────── -->
    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-4">— Press jam —</p>
      <h3 class="display-headline text-[28px] mb-2">The morning press wouldn't run.</h3>
      <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 max-w-md mx-auto">
        We couldn't load your digests. Please retry, or try again in a moment.
      </p>
      <button class="btn-editorial-ghost" @click="load">Retry</button>
    </section>

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
      <p class="kicker mb-5">— No issues yet —</p>
      <h3 class="display-headline text-[36px] leading-tight mb-3">
        Tomorrow's edition is
        <em class="italic-display">already on the press.</em>
      </h3>
      <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-7 max-w-md mx-auto">
        Curio files your first issue at 08:00. To make sure it knows what to write,
        set your beats now.
      </p>
      <router-link to="/onboarding" class="btn-editorial">
        Set my beats
        <span aria-hidden="true">→</span>
      </router-link>
    </section>

    <!-- ── Digest feed ──────────────────────────────────────── -->
    <section v-else class="space-y-12">
      <!-- Search — server-side full-text search over the whole retained archive
           (30 days), wider than the 7-day browse window below. -->
      <div class="pb-5 border-b border-[color:var(--rule)]">
        <label for="archive-search" class="kicker">Search the archive</label>
        <div class="mt-3 flex items-center gap-2">
          <input
            id="archive-search"
            v-model="searchQuery"
            type="search"
            placeholder="Search headlines and stories…"
            autocomplete="off"
            class="w-full max-w-md bg-transparent border border-[color:var(--rule)] px-3 py-2 font-body-curio text-[14px] text-[color:var(--ink)] placeholder:text-[color:var(--mute)] focus:border-[color:var(--ink)] focus:outline-none"
          />
          <button v-if="searchQuery" class="btn-editorial-ghost py-2 px-3" @click="clearSearch">
            Clear
          </button>
        </div>
        <p class="kicker mt-2 text-[color:var(--mute)]" aria-live="polite">
          <template v-if="searchLoading">Searching…</template>
          <template v-else-if="searchActive">
            {{ searchMatchCount }} {{ searchMatchCount === 1 ? 'story matches' : 'stories match' }} · full 30-day archive
          </template>
          <template v-else>Searches your full 30-day archive, beyond the 7-day view below</template>
        </p>
      </div>

      <!-- Topic filter — collapsed by default so the digest leads the page. The
           header toggle opens the full taxonomy grouped by big topic (L1); each
           big topic is itself a nested disclosure whose subtopics stay hidden
           until clicked. Heights animate via grid-template-rows 0fr→1fr. -->
      <div class="pb-2" aria-label="Filter digests by topic">
        <!-- Outer filter toggle -->
        <button
          type="button"
          class="group/filter w-full flex items-center justify-between gap-3 cursor-pointer select-none"
          :aria-expanded="filterOpen"
          aria-controls="beat-filter-panel"
          @click="filterOpen = !filterOpen"
        >
          <span class="flex items-baseline gap-3 min-w-0">
            <span class="kicker flex-shrink-0">Filter</span>
            <span
              class="inline-flex items-center px-3 py-1 text-[11px] font-mono-curio uppercase tracking-[0.14em] border max-w-full truncate"
              :class="activeTopicFilter
                ? 'bg-signal text-[color:var(--paper)] border-[color:var(--signal)]'
                : 'bg-ink text-[color:var(--paper)] border-[color:var(--ink)]'"
            >{{ activeTopicFilter || 'All beats' }}</span>
            <span v-if="!activeTopicFilter" class="kicker text-[color:var(--mute)] flex-shrink-0 hidden sm:inline">· {{ totalActiveBeats }} beats</span>
          </span>
          <span
            aria-hidden="true"
            class="font-mono-curio text-[13px] leading-none text-[color:var(--mute)] transition-all duration-300 ease-out motion-reduce:transition-none group-hover/filter:text-[color:var(--ink)]"
            :class="filterOpen ? 'rotate-90' : ''"
          >▸</span>
        </button>

        <!-- Collapsible filter panel -->
        <div
          id="beat-filter-panel"
          class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none"
          :class="filterOpen ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
        >
          <div class="overflow-hidden" :inert="!filterOpen">
            <div class="pt-4 space-y-3">
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  :aria-pressed="!activeTopicFilter"
                  :class="[
                    'px-3 py-1 text-[11px] font-mono-curio uppercase tracking-[0.14em] border transition-colors',
                    !activeTopicFilter
                      ? 'bg-ink text-[color:var(--paper)] border-[color:var(--ink)]'
                      : 'bg-transparent text-[color:var(--mute)] border-[color:var(--rule)] hover:text-[color:var(--ink)]'
                  ]"
                  @click="selectTopic('')"
                >
                  All beats
                </button>
              </div>

              <div
                v-for="group in groupedTopics"
                :key="group.id"
                class="border-t border-[color:var(--rule)] pt-3 first:border-t-0 first:pt-0"
              >
                <!-- Big-topic disclosure header -->
                <button
                  type="button"
                  class="group/head w-full flex items-center justify-between gap-3 cursor-pointer select-none py-0.5"
                  :aria-expanded="isGroupOpen(group.id)"
                  :aria-controls="`filter-group-${group.id}`"
                  @click="toggleGroup(group.id)"
                >
                  <span class="kicker flex items-center gap-1.5 text-[color:var(--ink-soft)] transition-colors group-hover/head:text-[color:var(--ink)]">
                    <AppIcon :name="group.icon" class="h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />
                    {{ group.name }}
                    <span class="text-[color:var(--mute)] normal-case tracking-normal">· {{ group.activeCount }} active</span>
                  </span>
                  <span
                    aria-hidden="true"
                    class="font-mono-curio text-[13px] leading-none text-[color:var(--mute)] transition-transform duration-300 ease-out motion-reduce:transition-none"
                    :class="isGroupOpen(group.id) ? 'rotate-90' : ''"
                  >▸</span>
                </button>

                <!-- Subtopics — hidden until the big topic is clicked -->
                <div
                  :id="`filter-group-${group.id}`"
                  class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none"
                  :class="isGroupOpen(group.id) ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
                >
                  <div class="overflow-hidden" :inert="!isGroupOpen(group.id)">
                    <div class="flex flex-wrap gap-2 pl-0.5 pt-3">
                      <button
                        v-for="item in group.topics"
                        :key="item.name"
                        type="button"
                        :disabled="item.count === 0"
                        :aria-pressed="item.count > 0 ? activeTopicFilter === item.name : undefined"
                        :class="[
                          'inline-flex items-baseline gap-1.5 px-3 py-1 text-[11px] font-mono-curio uppercase tracking-[0.14em] border transition-colors',
                          item.count === 0
                            ? 'bg-transparent text-[color:var(--mute)] border-[color:var(--rule)] opacity-40 cursor-not-allowed'
                            : activeTopicFilter === item.name
                              ? 'bg-signal text-[color:var(--paper)] border-[color:var(--signal)]'
                              : 'bg-transparent text-[color:var(--mute)] border-[color:var(--rule)] hover:text-[color:var(--ink)]'
                        ]"
                        @click="item.count > 0 && selectTopic(activeTopicFilter === item.name ? '' : item.name)"
                      >
                        {{ item.name }}
                        <span
                          :class="[
                            'num-tab text-[10px]',
                            activeTopicFilter === item.name ? 'opacity-80' : 'opacity-60'
                          ]"
                        >{{ item.count }}</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <p class="sr-only" aria-live="polite">
          {{ activeTopicFilter ? `Filter: ${activeTopicFilter}` : 'No topic filter' }}
        </p>
      </div>

      <!-- No issues match the search and/or active beat (archive itself is non-empty) -->
      <div
        v-if="filteredDigests.length === 0 && !searchLoading"
        class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center"
      >
        <template v-if="searchActive">
          <p class="kicker mb-3">— No matches —</p>
          <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 max-w-md mx-auto">
            Nothing in your archive matches <strong>"{{ searchQuery.trim() }}"</strong><template v-if="activeTopicFilter"> on <strong>{{ activeTopicFilter }}</strong></template>.
          </p>
          <button class="btn-editorial-ghost" @click="clearSearch">Clear search</button>
        </template>
        <template v-else>
          <p class="kicker mb-3">— No issues on this beat —</p>
          <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 max-w-md mx-auto">
            None of your archived editions cover <strong>{{ activeTopicFilter }}</strong> yet.
          </p>
          <button class="btn-editorial-ghost" @click="selectTopic('')">Show all beats</button>
        </template>
      </div>

      <!-- Current day heading — one day per page, within the last 7 days -->
      <div v-if="currentDay" class="flex items-baseline justify-between gap-3 flex-wrap">
        <p class="kicker kicker-signal">{{ currentDay.label }}</p>
        <p class="kicker text-[color:var(--mute)]">
          Day {{ localPage + 1 }} of {{ totalPages }} · {{ searchActive ? 'search results' : 'last 7 days' }}
        </p>
      </div>

      <!-- Issues -->
      <article
        v-for="(digest, idx) in pagedDigests"
        :key="digest.id"
        class="digest-card"
      >
        <!-- Issue masthead -->
        <header class="border-t-2 border-[color:var(--rule)] pt-5 mb-7">
          <div class="flex items-end justify-between flex-wrap gap-3 mb-4">
            <div>
              <p class="kicker">Issue {{ issueNumberFor(digest.id) }} · {{ formatDate(digest.generatedAt) }}</p>
            </div>
            <div class="flex items-center gap-3">
              <span
                v-if="digest.emailSentAt"
                class="kicker"
                style="color: var(--leaf);"
              >
                ● Delivered {{ formatTime(digest.emailSentAt) }}
              </span>
              <router-link
                :to="{ name: 'quiz', params: { digestId: digest.id } }"
                class="btn-editorial-ghost py-2 px-3"
                style="font-size: 0.625rem;"
              >
                Take quiz
                <span aria-hidden="true">→</span>
              </router-link>
            </div>
          </div>
          <div
            v-if="getTopics(digest).length > 0"
            class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] font-body-curio text-[color:var(--mute)]"
          >
            <span class="kicker">Beats</span>
            <span
              v-for="(topic, i) in getTopics(digest)"
              :key="topic"
              class="flex items-center gap-3"
            >
              {{ topic }}
              <span v-if="i < getTopics(digest).length - 1" class="text-[color:var(--rule)]">·</span>
            </span>
          </div>
        </header>

        <!-- Stories (narrowed to the active beat when a filter is on) -->
        <ol class="space-y-10">
          <li
            v-for="(summary, i) in visibleSummaries(digest)"
            :key="i"
            class="grid grid-cols-12 gap-x-6 gap-y-2"
          >
            <!-- Story number gutter -->
            <div class="col-span-12 sm:col-span-1">
              <span class="deco-num text-[28px] leading-none text-[color:var(--mute)]">{{ String(i + 1).padStart(2, '0') }}</span>
            </div>

            <!-- Body -->
            <div class="col-span-12 sm:col-span-11">
              <p class="kicker mb-2">{{ summary.topic || 'Story' }}</p>
              <h3 class="display-headline text-[clamp(22px,2.4vw,32px)] leading-[1.05] mb-3">
                {{ summary.headline }}
              </h3>
              <p
                :class="[
                  'font-body-curio text-[15.5px] leading-[1.65] text-[color:var(--ink)] mb-4',
                  idx === 0 && i === 0 ? 'dropcap' : ''
                ]"
              >
                {{ summary.summary }}
              </p>
              <p
                v-if="summary.why_it_matters"
                class="border-l-2 border-[color:var(--signal)] pl-4 font-display italic text-[15px] text-[color:var(--ink-soft)] leading-snug mb-4"
              >
                <span class="kicker kicker-signal not-italic mr-2">Why it matters</span>
                {{ summary.why_it_matters }}
              </p>
              <a
                v-if="safeExternalUrl(summary.source_url)"
                :href="safeExternalUrl(summary.source_url) ?? undefined"
                target="_blank"
                rel="noopener noreferrer"
                class="ink-link font-mono-curio text-[11px] uppercase tracking-[0.14em]"
              >
                Source: {{ summary.source_name || 'link' }} →
              </a>
            </div>
          </li>
        </ol>

        <!-- Issue end mark -->
        <div class="mt-10 flex items-center justify-center">
          <span class="kicker">— End of issue —</span>
        </div>
      </article>

      <!-- Pagination — one day per page (most recent first, last 7 days only) -->
      <nav v-if="totalPages > 1" class="flex items-center justify-between border-t-2 border-[color:var(--rule)] pt-6">
        <button
          class="btn-editorial-ghost"
          :disabled="localPage === 0"
          @click="goToPage(localPage - 1)"
        >
          <span aria-hidden="true">←</span>
          Newer
        </button>
        <span class="kicker">
          Day <span class="num-tab text-[color:var(--ink)]">{{ localPage + 1 }}</span>
          of <span class="num-tab text-[color:var(--ink)]">{{ totalPages }}</span>
        </span>
        <button
          class="btn-editorial-ghost"
          :disabled="localPage >= totalPages - 1"
          @click="goToPage(localPage + 1)"
        >
          Older
          <span aria-hidden="true">→</span>
        </button>
      </nav>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useNewsStore } from '@/stores/news'
import { useToast } from '@/composables/useToast'
import { safeExternalUrl } from '@/utils/safeUrl'
import { TOPIC_HIERARCHY, TOPIC_DOMAIN_MAP } from '@/data/topics'
import type { TopicIcon } from '@/data/topics'
import type { Digest, NewsSummary } from '@/types/news'
import AppIcon from '@/components/ui/AppIcon.vue'
import { getApiErrorMessage } from '@/utils/apiError'

const newsStore = useNewsStore()
const { digests, topicFilter } = storeToRefs(newsStore)
const { error: showError } = useToast()

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
      showError(getApiErrorMessage(err, 'Search failed'))
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

// The whole filter is collapsed by default so the digest leads the page; the
// user opens it to reveal the grouped beats.
const filterOpen = ref(false)

// Which big-topic (L1) groups are expanded inside the filter. Collapsed by
// default; clicking a big topic reveals its subtopics (the disclosure region
// animates open/closed via grid-template-rows).
const expandedGroups = ref<Set<string>>(new Set())
const toggleGroup = (id: string) => {
  const next = new Set(expandedGroups.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedGroups.value = next
}
const isGroupOpen = (id: string) => expandedGroups.value.has(id)

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
// distribution: every domain and every leaf is shown, with a per-beat digest
// count. Beats with no digests render disabled/greyed. Each group carries an
// `activeCount` (subtopics with digests) shown on its collapsed header.
interface TopicItem {
  name: string
  count: number
}
interface TopicGroup {
  id: string
  name: string
  icon: TopicIcon | 'archive'
  topics: TopicItem[]
  activeCount: number
}

const groupedTopics = computed<TopicGroup[]>(() => {
  const counts = topicCounts.value

  const groups: TopicGroup[] = TOPIC_HIERARCHY.map((domain) => {
    const topics: TopicItem[] = domain.subcategories
      .flatMap((sub) => sub.topics)
      .map((name) => ({ name, count: counts[name] ?? 0 }))
    return {
      id: domain.id,
      name: domain.name,
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
      name: 'Other',
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

// Defined before dayGroups (which calls formatDate) so the watch(totalPages)
// below — which evaluates dayGroups during setup — never hits a temporal dead
// zone once the digest store is already populated (e.g. on back-navigation).
const formatDate = (dateStr: string) =>
  new Date(dateStr).toLocaleDateString('en-US', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })

const formatTime = (dateStr: string) =>
  new Date(dateStr).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })

// A digest is shown only if it has at least one story on the active beat, so
// filtering an all-beats daily edition narrows it instead of showing everything.
const filteredDigests = computed(() =>
  activeTopicFilter.value
    ? baseDigests.value.filter((d) =>
        (d.content?.summaries ?? []).some((s) => s.topic === activeTopicFilter.value)
      )
    : baseDigests.value
)

// Group the visible (beat-filtered) digests by calendar day, newest first.
// Each page of the archive is one day's edition(s) — "pagination for each day".
interface DayGroup {
  key: string
  label: string
  digests: Digest[]
}

const dayKey = (dateStr: string) => {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const dayGroups = computed<DayGroup[]>(() => {
  const map = new Map<string, Digest[]>()
  for (const d of filteredDigests.value) {
    const k = dayKey(d.generatedAt)
    const bucket = map.get(k)
    if (bucket) bucket.push(d)
    else map.set(k, [d])
  }
  return [...map.entries()]
    .sort((a, b) => (a[0] < b[0] ? 1 : -1)) // newest day first
    .map(([key, ds]) => ({
      key,
      label: formatDate(ds[0]!.generatedAt),
      digests: [...ds].sort(
        (x, y) => new Date(y.generatedAt).getTime() - new Date(x.generatedAt).getTime()
      ),
    }))
})

const totalPages = computed(() => Math.max(1, dayGroups.value.length))

const currentDay = computed(() => dayGroups.value[localPage.value] ?? null)

const pagedDigests = computed(() => currentDay.value?.digests ?? [])

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
    showError(getApiErrorMessage(err, 'Failed to load digests'))
  } finally {
    loading.value = false
  }
}

const getTopics = (digest: { content?: { generatedFor?: string[] } }): string[] =>
  digest.content?.generatedFor ?? []

// The stories shown for a digest: narrowed by the active beat and, during a
// search, to the stories that actually match the query. Renumbers from 01 so a
// filtered view reads as its own edition.
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

// Stories the active search will actually render (post beat-filter + narrowing),
// so the results copy counts what the reader sees, not whole issues.
const searchMatchCount = computed(() =>
  filteredDigests.value.reduce((n, d) => n + visibleSummaries(d).length, 0)
)

// Stable issue number based on digest id (last 3 chars hex → decimal-ish).
const issueNumberFor = (id: string) => {
  const slice = id.slice(-3)
  const n = parseInt(slice, 16)
  return Number.isFinite(n) ? String(n).padStart(3, '0') : '???'
}

onMounted(load)
</script>

<style scoped>
.digest-card {
  content-visibility: auto;
  contain-intrinsic-size: 1px 800px;
}
</style>
