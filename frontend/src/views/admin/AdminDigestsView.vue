<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">{{ t('admin.digests.kicker') }}</p>
      <i18n-t scope="global" keypath="admin.digests.headline" tag="h1" class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        <template #editions><em class="italic-display">{{ t('admin.digests.editions') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full"></div>
    </header>

    <!-- Filters -->
    <div class="mb-6 grid grid-cols-1 sm:grid-cols-3 gap-4 border-t border-b border-[color:var(--rule)] py-4">
      <div>
        <label for="filter-topic" class="kicker mb-1 block">{{ t('admin.digests.filters.topic') }}</label>
        <select
          id="filter-topic"
          v-model="filters.topic"
          class="w-full bg-transparent border-b border-[color:var(--rule)] py-1.5 font-display text-[15px] focus:outline-none focus:border-[color:var(--signal)]"
          @change="loadDigests(0)"
        >
          <option value="">{{ t('admin.common.allBeats') }}</option>
          <option v-for="topic in allTopics" :key="topic" :value="topic">{{ topicLabel(topic) }}</option>
        </select>
      </div>
      <div>
        <label for="filter-user" class="kicker mb-1 block">{{ t('admin.digests.filters.user') }}</label>
        <input
          id="filter-user"
          v-model="filters.userSearch"
          type="text"
          :placeholder="t('admin.digests.filters.userPlaceholder')"
          class="w-full bg-transparent border-b border-[color:var(--rule)] py-1.5 font-display text-[15px] focus:outline-none focus:border-[color:var(--signal)] placeholder:font-body-curio placeholder:text-[14px] placeholder:text-[color:var(--mute)]"
          @keyup.enter="loadDigests(0)"
        />
      </div>
      <div class="flex items-end">
        <button class="btn-editorial w-full justify-center" @click="loadDigests(0)">
          {{ t('admin.common.search') }}
          <span aria-hidden="true">→</span>
        </button>
      </div>
    </div>

    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-3">{{ t('admin.common.loadFailed') }}</p>
      <button class="btn-editorial-ghost" @click="loadDigests(page)">{{ t('admin.common.retry') }}</button>
    </section>

    <section v-else-if="loading" class="border-t-2 border-[color:var(--rule)] pt-12 text-center">
      <p class="kicker">{{ t('admin.digests.loading') }}</p>
    </section>

    <section v-else>
      <div class="mb-3 flex items-center justify-between">
        <p class="kicker">{{ t('admin.digests.count', totalElements) }}</p>
        <div class="flex items-baseline gap-3">
          <label for="page-size" class="kicker">{{ t('admin.digests.perPage') }}</label>
          <select
            id="page-size"
            v-model.number="pageSize"
            class="bg-transparent border-b border-[color:var(--rule)] font-mono-curio text-[12px] uppercase tracking-[0.14em] focus:outline-none"
            @change="loadDigests(0)"
          >
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
        </div>
      </div>

      <table class="w-full text-[13.5px] border-t-2 border-[color:var(--rule)]">
        <thead>
          <tr class="border-b border-[color:var(--rule)]">
            <th class="text-left py-3 kicker">{{ t('admin.digests.columns.reader') }}</th>
            <th class="text-left py-3 kicker">{{ t('admin.common.columns.beats') }}</th>
            <th class="text-right py-3 kicker">{{ t('admin.digests.columns.filed') }}</th>
            <th class="text-right py-3 kicker">{{ t('admin.digests.columns.delivered') }}</th>
            <th class="text-right py-3 kicker">{{ t('admin.digests.columns.quiz') }}</th>
            <th class="text-right py-3 kicker"></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="digest in digests" :key="digest.id">
            <tr class="border-b border-[color:var(--rule)]/40">
              <td class="py-3">
                <p class="font-display text-[15px] text-[color:var(--ink)]">{{ digest.userEmail }}</p>
                <p v-if="digest.userFullName" class="kicker mt-0.5">{{ digest.userFullName }}</p>
              </td>
              <td class="py-3">
                <div class="flex flex-wrap gap-x-2 gap-y-1">
                  <span
                    v-for="topic in digestTopics(digest)"
                    :key="topic"
                    class="font-mono-curio text-[10px] uppercase tracking-[0.12em] text-[color:var(--ink-soft)]"
                  >
                    {{ topicLabel(topic) }}
                  </span>
                </div>
              </td>
              <td class="py-3 text-right font-mono-curio text-[12px] text-[color:var(--ink-soft)]">{{ formatDate(digest.generatedAt) }}</td>
              <td class="py-3 text-right">
                <span class="kicker" :style="digest.emailSentAt ? 'color: var(--leaf)' : 'color: var(--mute)'">
                  {{ digest.emailSentAt ? '● ' + formatDate(digest.emailSentAt) : '○ ' + t('admin.digests.notSent') }}
                </span>
              </td>
              <td class="py-3 text-right">
                <span class="kicker text-[color:var(--mute)]">
                  {{ t('admin.digests.stories', { count: digest.content?.summaries?.length ?? 0 }) }}
                </span>
              </td>
              <td class="py-3 text-right">
                <button class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]" @click="toggleExpand(digest.id)">
                  {{ expandedId === digest.id ? t('admin.digests.collapse') : t('admin.digests.expand') }}
                </button>
              </td>
            </tr>
            <tr v-if="expandedId === digest.id" class="border-b border-[color:var(--rule)]/40">
              <td colspan="6" class="bg-paper-deep px-4 py-4">
                <dl class="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2 text-[13px]">
                  <div><dt class="kicker">{{ t('admin.digests.detail.digestId') }}</dt><dd class="font-mono-curio text-[11.5px] break-all">{{ digest.id }}</dd></div>
                  <div><dt class="kicker">{{ t('admin.digests.detail.userId') }}</dt><dd class="font-mono-curio text-[11.5px] break-all">{{ digest.userId }}</dd></div>
                  <div><dt class="kicker">{{ t('admin.digests.detail.stories') }}</dt><dd class="font-display">{{ digest.content?.summaries?.length ?? 0 }}</dd></div>
                  <div><dt class="kicker">{{ t('admin.common.columns.beats') }}</dt><dd class="font-display">{{ digestTopics(digest).map(topicLabel).join(' · ') || '—' }}</dd></div>
                </dl>
              </td>
            </tr>
          </template>
          <tr v-if="digests.length === 0">
            <td colspan="6" class="py-8 text-center kicker">{{ t('admin.digests.empty') }}</td>
          </tr>
        </tbody>
      </table>

      <nav v-if="totalPages > 1" class="flex items-center justify-between border-t-2 border-[color:var(--rule)] pt-6 mt-2">
        <button class="btn-editorial-ghost" :disabled="page === 0" @click="loadDigests(page - 1)">
          <span aria-hidden="true">←</span>
          {{ t('admin.common.pagination.earlier') }}
        </button>
        <i18n-t scope="global" keypath="admin.common.pagination.pageOf" tag="span" class="kicker">
          <template #page><span class="num-tab text-[color:var(--ink)]">{{ page + 1 }}</span></template>
          <template #total><span class="num-tab text-[color:var(--ink)]">{{ totalPages }}</span></template>
        </i18n-t>
        <button class="btn-editorial-ghost" :disabled="page >= totalPages - 1" @click="loadDigests(page + 1)">
          {{ t('admin.common.pagination.older') }}
          <span aria-hidden="true">→</span>
        </button>
      </nav>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { ALL_TOPICS } from '@/data/topics'

const { t } = useI18n()
const { intlLocale } = useLocale()
const { topicLabel } = useTopicLabels()
const { error } = useToast()

// Canonical topic names: the backend filters on them, so the option VALUE is
// the raw name and only the visible label goes through topicLabel().
const allTopics = ALL_TOPICS

interface DigestEntry {
  id: string
  userId: string
  userEmail: string
  userFullName: string | null
  content?: { generatedFor?: string[]; summaries?: Array<{ topic?: string }> } | null
  generatedAt: string
  emailSentAt: string | null
}

// Backend AdminDigestResponse only exposes content + sent-at metadata;
// derive the rest for display.
const digestTopics = (d: DigestEntry): string[] => {
  if (d.content?.generatedFor && d.content.generatedFor.length) return d.content.generatedFor
  if (d.content?.summaries) {
    const set = new Set<string>()
    for (const s of d.content.summaries) if (s.topic) set.add(s.topic)
    return [...set]
  }
  return []
}

const loading = ref(false)
const errorOccurred = ref(false)
const digests = ref<DigestEntry[]>([])
const page = ref(0)
const pageSize = ref(20)
const totalPages = ref(0)
const totalElements = ref(0)
const expandedId = ref<string | null>(null)

const filters = reactive({ topic: '', userSearch: '' })

const formatDate = (value: string) =>
  new Date(value).toLocaleDateString(intlLocale.value, {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })

const toggleExpand = (id: string) => {
  expandedId.value = expandedId.value === id ? null : id
}

const loadDigests = async (nextPage = 0) => {
  loading.value = true
  errorOccurred.value = false
  expandedId.value = null
  try {
    const params: Record<string, unknown> = { page: nextPage, size: pageSize.value }
    if (filters.topic) params.topic = filters.topic
    if (filters.userSearch.trim()) params.userEmail = filters.userSearch.trim()
    const response = await api.admin.getDigests(params as { page?: number; size?: number; topic?: string; userEmail?: string })
    digests.value = response.data.content
    page.value = response.data.number
    totalPages.value = response.data.totalPages
    totalElements.value = response.data.totalElements
  } catch {
    errorOccurred.value = true
    error(t('admin.digests.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(() => loadDigests(0))
</script>
