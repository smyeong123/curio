<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <header class="mb-10">
      <router-link
        to="/admin/users"
        class="kicker mb-4 inline-flex items-baseline gap-2 hover:text-[color:var(--ink)]"
      >
        <span aria-hidden="true">←</span> {{ t('admin.userDetail.back') }}
      </router-link>
      <p class="kicker kicker-signal mb-3">{{ t('admin.userDetail.kicker') }}</p>
      <i18n-t scope="global" keypath="admin.userDetail.headline" tag="h1" class="display-headline text-[clamp(40px,6vw,72px)] leading-[0.95]">
        <template #details><em class="italic-display">{{ t('admin.userDetail.details') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full mt-6"></div>
    </header>

    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-3">{{ t('admin.common.loadFailed') }}</p>
      <button class="btn-editorial-ghost" @click="loadUser">{{ t('admin.common.retry') }}</button>
    </section>

    <section v-else-if="loading" class="border-t-2 border-[color:var(--rule)] pt-12 text-center">
      <p class="kicker">{{ t('admin.userDetail.loading') }}</p>
    </section>

    <section v-else-if="!detail" class="border-t-2 border-[color:var(--rule)] pt-12 text-center">
      <p class="kicker">{{ t('admin.userDetail.notFound') }}</p>
    </section>

    <div v-else class="space-y-12">
      <!-- Profile -->
      <section>
        <p class="kicker mb-4">{{ t('admin.userDetail.sections.profile') }}</p>
        <dl class="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4 border-t-2 border-b-2 border-[color:var(--rule)] py-5">
          <div v-for="field in profileFields" :key="field.label">
            <dt class="kicker mb-1">{{ field.label }}</dt>
            <dd class="font-display text-[16px] text-[color:var(--ink)]">{{ field.value }}</dd>
          </div>
        </dl>
      </section>

      <!-- Beats -->
      <section>
        <p class="kicker mb-4">{{ t('admin.userDetail.sections.beats') }}</p>
        <div class="border-t-2 border-b-2 border-[color:var(--rule)] py-5">
          <div v-if="detail.topics.length === 0" class="kicker">{{ t('admin.userDetail.noBeats') }}</div>
          <div v-else class="flex flex-wrap gap-2">
            <span
              v-for="topic in detail.topics"
              :key="topic"
              class="px-3 py-1.5 border border-[color:var(--rule)] bg-paper font-display text-[13px]"
            >
              {{ topicLabel(topic) }}
            </span>
          </div>
        </div>
      </section>

      <!-- Metrics -->
      <section>
        <p class="kicker mb-4">{{ t('admin.userDetail.sections.metrics') }}</p>
        <div class="grid grid-cols-3 border-t-2 border-b-2 border-[color:var(--rule)] divide-x divide-[color:var(--rule)]">
          <div class="px-5 py-6">
            <div class="deco-num text-[44px] leading-none">{{ detail.metrics.digestCount }}</div>
            <p class="kicker mt-3">{{ t('admin.userDetail.metrics.digests') }}</p>
          </div>
          <div class="px-5 py-6">
            <div class="deco-num text-[44px] leading-none text-[color:var(--signal-deep)]">{{ detail.metrics.quizAttempts }}</div>
            <p class="kicker mt-3">{{ t('admin.userDetail.metrics.quizAttempts') }}</p>
          </div>
          <div class="px-5 py-6">
            <div class="deco-num text-[44px] leading-none text-[color:var(--leaf)]">
              {{ detail.metrics.averageQuizScore ?? '—' }}
            </div>
            <p class="kicker mt-3">{{ t('admin.userDetail.metrics.avgScore') }}</p>
          </div>
        </div>
      </section>

      <!-- Recent activity -->
      <section class="grid grid-cols-1 md:grid-cols-2 gap-0 border-t-2 border-b-2 border-[color:var(--rule)]">
        <div class="p-6 md:border-r border-[color:var(--rule)]">
          <p class="kicker mb-4">{{ t('admin.userDetail.sections.recentDigests') }}</p>
          <ul class="space-y-2">
            <li
              v-for="digest in detail.recentDigests"
              :key="digest.id"
              class="font-body-curio text-[14px] text-[color:var(--ink)] flex items-baseline gap-3"
            >
              <span class="num-tab text-[10px] text-[color:var(--mute)]">●</span>
              {{ formatDate(digest.generatedAt) }}
            </li>
            <li v-if="detail.recentDigests.length === 0" class="kicker">{{ t('admin.userDetail.noDigests') }}</li>
          </ul>
        </div>
        <div class="p-6">
          <p class="kicker mb-4">{{ t('admin.userDetail.sections.recentQuizzes') }}</p>
          <ul class="space-y-2">
            <li
              v-for="attempt in detail.recentQuizAttempts"
              :key="attempt.id"
              class="font-body-curio text-[14px] text-[color:var(--ink)] flex items-baseline justify-between"
            >
              <span>{{ formatDate(attempt.completedAt) }}</span>
              <span class="num-tab text-[14px] font-bold">{{ t('admin.userDetail.pts', { score: attempt.score }) }}</span>
            </li>
            <li v-if="detail.recentQuizAttempts.length === 0" class="kicker">{{ t('admin.userDetail.noQuizzes') }}</li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'
import { useTopicLabels } from '@/composables/useTopicLabels'

const route = useRoute()
const { t } = useI18n()
const { intlLocale } = useLocale()
const { topicLabel } = useTopicLabels()
const { error } = useToast()

interface UserDetail {
  user: import('@/types/user').User
  topics: string[]
  metrics: {
    digestCount: number
    quizAttempts: number
    averageQuizScore: number | null
  }
  recentDigests: { id: string; generatedAt: string }[]
  recentQuizAttempts: { id: string; completedAt: string; score: number }[]
}

const loading = ref(false)
const errorOccurred = ref(false)
const detail = ref<UserDetail | null>(null)

// Declared before profileFields (a computed that calls it) so evaluating the
// computed during setup can never hit a temporal dead zone.
const formatDate = (value: string) =>
  new Date(value).toLocaleDateString(intlLocale.value, { year: 'numeric', month: 'short', day: 'numeric' })

// Computed (not a plain array) so labels and dates re-render on an edition switch.
const profileFields = computed(() => {
  if (!detail.value) return []
  const u = detail.value.user
  return [
    { label: t('admin.common.columns.email'), value: u.email },
    { label: t('admin.common.columns.name'), value: u.fullName || '—' },
    { label: t('admin.userDetail.profile.admin'), value: u.isAdmin ? t('admin.common.yes') : t('admin.common.no') },
    { label: t('admin.common.columns.delivery'), value: u.deliveryEnabled ? t('admin.common.on') : t('admin.common.off') },
    { label: t('admin.userDetail.profile.verified'), value: u.emailVerified ? t('admin.common.yes') : t('admin.common.no') },
    { label: t('admin.common.columns.joined'), value: formatDate(u.createdAt) },
  ]
})

const loadUser = async () => {
  const id = route.params.id as string
  loading.value = true
  errorOccurred.value = false
  try {
    const response = await api.admin.getUser(id)
    detail.value = response.data
  } catch {
    errorOccurred.value = true
    error(t('admin.userDetail.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(() => loadUser())
</script>
