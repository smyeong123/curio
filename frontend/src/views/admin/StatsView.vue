<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead
      :kicker="t('admin.stats.kicker')"
      keypath="admin.stats.headline"
      emphasis="numbers"
      :emphasis-text="t('admin.stats.numbers')"
    />

    <ErrorState
      v-if="errorOccurred"
      :kicker="t('admin.common.loadFailed')"
      @retry="loadStats"
    />

    <section v-else-if="loading" class="border-t-2 border-[color:var(--rule)] pt-12 text-center">
      <p class="kicker">{{ t('admin.stats.loading') }}</p>
    </section>

    <div v-else class="space-y-12">
      <!-- Totals -->
      <section class="grid grid-cols-3 border-t-2 border-b-2 border-[color:var(--rule)] divide-x divide-[color:var(--rule)]">
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none">{{ formatNumber(stats.totalUsers) }}</div>
          <p class="kicker mt-3">{{ t('admin.stats.totals.totalUsers') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--leaf)]">{{ formatNumber(stats.emailsSentToday) }}</div>
          <p class="kicker mt-3">{{ t('admin.common.kpi.emailsToday') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--signal-deep)]">{{ formatNumber(stats.quizCompletionsToday) }}</div>
          <p class="kicker mt-3">{{ t('admin.common.kpi.quizzesToday') }}</p>
        </div>
      </section>

      <!-- Topic distribution -->
      <section>
        <p class="kicker mb-4">{{ t('admin.stats.distribution.kicker') }}</p>
        <div v-if="topicEntries.length === 0" class="kicker">{{ t('admin.stats.distribution.empty') }}</div>
        <div v-else class="space-y-8">
          <article v-for="domain in topicsByDomain" :key="domain.id">
            <div class="flex items-baseline justify-between mb-3 border-b border-[color:var(--rule)] pb-2">
              <p class="font-display text-[18px] flex items-center gap-2">
                <AppIcon :name="domain.icon" class="h-[18px] w-[18px] flex-shrink-0 text-[color:var(--signal-deep)]" />
                {{ groupName(domain) }}
              </p>
              <p class="kicker">{{ t('admin.stats.distribution.subscribers', { count: domain.totalSubscribers }) }}</p>
            </div>
            <div class="space-y-2">
              <div v-for="entry in domain.entries" :key="entry.topic" class="flex items-center gap-4">
                <span class="font-display text-[14px] w-56 flex-shrink-0 truncate">{{ topicLabel(entry.topic) }}</span>
                <div class="flex-1 h-2 border border-[color:var(--rule)]">
                  <div
                    class="h-full bg-[color:var(--signal)]"
                    :style="{ width: maxCount > 0 ? `${(entry.count / maxCount) * 100}%` : '0%' }"
                  ></div>
                </div>
                <span class="num-tab text-[14px] w-10 text-right flex-shrink-0 font-bold">{{ entry.count }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useFormat } from '@/composables/useFormat'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { TOPIC_HIERARCHY } from '@/data/topics'
import type { AdminStats } from '@/types/admin'
import AppIcon from '@/components/ui/AppIcon.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageMasthead from '@/components/ui/PageMasthead.vue'

const { t } = useI18n()
const { formatNumber } = useFormat()
const { topicLabel, groupName } = useTopicLabels()
const { error } = useToast()

const loading = ref(false)
const errorOccurred = ref(false)
const stats = ref<AdminStats>({ totalUsers: 0, emailsSentToday: 0, quizCompletionsToday: 0 })
const topicDistribution = ref<Record<string, number>>({})

const topicEntries = computed(() =>
  Object.entries(topicDistribution.value)
    .map(([topic, count]) => ({ topic, count }))
    .sort((a, b) => b.count - a.count)
)

const maxCount = computed(() => Math.max(...topicEntries.value.map((e) => e.count), 1))

// Keys stay canonical (topic names / group ids); labels are resolved at render.
const topicsByDomain = computed(() =>
  TOPIC_HIERARCHY.map((domain) => {
    const domainTopics = domain.subcategories.flatMap((s) => s.topics)
    const entries = domainTopics
      .map((topic) => ({ topic, count: topicDistribution.value[topic] ?? 0 }))
      .sort((a, b) => b.count - a.count)
    return {
      id: domain.id,
      name: domain.name,
      icon: domain.icon,
      entries,
      totalSubscribers: entries.reduce((sum, e) => sum + e.count, 0),
    }
  })
)

const loadStats = async () => {
  loading.value = true
  errorOccurred.value = false
  try {
    const [statsResponse, topicResponse] = await Promise.all([
      api.admin.getStats(),
      api.admin.getTopicDistribution(),
    ])
    stats.value = statsResponse.data
    topicDistribution.value = topicResponse.data || {}
  } catch {
    errorOccurred.value = true
    error(t('admin.stats.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(() => loadStats())
</script>
